package dr.evomodel.antigenic;

import dr.inference.model.*;
import dr.util.Author;
import dr.util.Citable;
import dr.util.Citation;
import dr.xml.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Andrew Rambaut
 * @author Trevor Bedford
 * @author Marc Suchard
 * @version $Id$
 */
public class AntigenicSplitPrior extends AbstractModelLikelihood implements Citable {

    public final static String ANTIGENIC_SPLIT_PRIOR = "antigenicSplitPrior";

    public AntigenicSplitPrior(
            MatrixParameter locationsParameter,
            Parameter datesParameter,
            Parameter regressionSlopeParameter,
            Parameter regressionPrecisionParameter,
            Parameter splitTimeParameter,
            Parameter splitAngleParameter,
            Parameter splitAssignmentParameter
    ) {

        super(ANTIGENIC_SPLIT_PRIOR);

        this.locationsParameter = locationsParameter;
        addVariable(this.locationsParameter);

        this.datesParameter = datesParameter;
        addVariable(this.datesParameter);

        dimension = locationsParameter.getParameter(0).getDimension();
        count = locationsParameter.getParameterCount();

        earliestDate = datesParameter.getParameterValue(0);
        double latestDate = datesParameter.getParameterValue(0);
        for (int i=0; i<count; i++) {
            double date = datesParameter.getParameterValue(i);
            if (earliestDate > date) {
                earliestDate = date;
            }
            if (latestDate < date) {
                latestDate = date;
            }
        }
        double timeSpan = latestDate - earliestDate;

        this.regressionSlopeParameter = regressionSlopeParameter;
        addVariable(regressionSlopeParameter);
        regressionSlopeParameter.addBounds(new Parameter.DefaultBounds(Double.MAX_VALUE, 0.0, 1));

        this.regressionPrecisionParameter = regressionPrecisionParameter;
        addVariable(regressionPrecisionParameter);
        regressionPrecisionParameter.addBounds(new Parameter.DefaultBounds(Double.MAX_VALUE, 0.0, 1));

        this.splitTimeParameter = splitTimeParameter;
        addVariable(splitTimeParameter);
        splitTimeParameter.addBounds(new Parameter.DefaultBounds(50.0, 20.0, 1));

        this.splitAngleParameter = splitAngleParameter;
        addVariable(splitAngleParameter);
        splitAngleParameter.addBounds(new Parameter.DefaultBounds(0.5*Math.PI, 0.5, 1));

        this.splitAssignmentParameter = splitAssignmentParameter;
        addVariable(splitAssignmentParameter);
        splitAssignmentParameter.addBounds(new Parameter.DefaultBounds(1.0, 0.0, 1));
        String[] labelArray = new String[count];
        splitAssignmentParameter.setDimension(count);
        for (int i = 0; i < count; i++) {
            labelArray[i] = datesParameter.getDimensionName(i);
            splitAssignmentParameter.setParameterValueQuietly(i, 0.0);
        }
        splitAssignmentParameter.setDimensionNames(labelArray);

        likelihoodKnown = false;



    }


    @Override
    protected void handleModelChangedEvent(Model model, Object object, int index) {
    }

    @Override
    protected void handleVariableChangedEvent(Variable variable, int index, Variable.ChangeType type) {
        if (variable == locationsParameter || variable == datesParameter
            || variable == regressionSlopeParameter || variable == regressionPrecisionParameter
            || variable == splitTimeParameter || variable == splitAngleParameter
            || variable == splitAssignmentParameter) {
            likelihoodKnown = false;
        }
    }

    @Override
    protected void storeState() {
        storedLogLikelihood = logLikelihood;
    }

    @Override
    protected void restoreState() {
        logLikelihood = storedLogLikelihood;
        likelihoodKnown = false;
    }

    @Override
    protected void acceptState() {
    }

    @Override
    public Model getModel() {
        return this;
    }

    @Override
    public double getLogLikelihood() {
        if (!likelihoodKnown) {
            logLikelihood = computeLogLikelihood();
        }
        return logLikelihood;
    }

    private double computeLogLikelihood() {

        double precision = regressionPrecisionParameter.getParameterValue(0);
        double logLikelihood = (0.5 * Math.log(precision) * count) - (0.5 * precision * sumOfSquaredResiduals());
        likelihoodKnown = true;
        return logLikelihood;

    }

    // go through each location and compute sum of squared residuals from regression line
    protected double sumOfSquaredResiduals() {

        double ssr = 0.0;

        for (int i=0; i < count; i++) {

            Parameter loc = locationsParameter.getParameter(i);

            double x = loc.getParameterValue(0);
            double y = expectedAG1(i);
            ssr += (x - y) * (x - y);

            if (dimension > 1) {
                x = loc.getParameterValue(1);
                y = expectedAG2(i);
                ssr += (x - y) * (x - y);
            }

            for (int j=2; j < dimension; j++) {
                x = loc.getParameterValue(j);
                ssr += x*x;
            }

        }

        return ssr;
    }

    // given a location index, calculate the expected AG1 value
    protected double expectedAG1(int index) {

        double date = datesParameter.getParameterValue(index);

        double ag1 = 0;
        double time = date - earliestDate;
        double splitTime = splitTimeParameter.getParameterValue(0);
        double splitAngle = splitAngleParameter.getParameterValue(0);
        double beta = regressionSlopeParameter.getParameterValue(0);

        if (time <= splitTime) {
            ag1 = beta * time;
        }
        else {
            ag1 = (beta * splitTime) + (beta * (time-splitTime) * Math.cos(splitAngle));
        }

        return ag1;

    }

    // given a location index, calculate the expected AG2 value of top branch
    protected double expectedAG2(int index) {

        double date = datesParameter.getParameterValue(index);
        int assignment = (int) splitAssignmentParameter.getParameterValue(index);

        double ag2 = 0;
        double time = date - earliestDate;
        double splitTime = splitTimeParameter.getParameterValue(0);
        double splitAngle = splitAngleParameter.getParameterValue(0);
        double beta = regressionSlopeParameter.getParameterValue(0);

        if (time <= splitTime) {
            ag2 = 0;
        }
        else {
            ag2 = beta * (time-splitTime) * Math.sin(splitAngle);
        }

        if (assignment == 1) {
            ag2 = -1*ag2;
        }

        return ag2;

    }

    @Override
    public void makeDirty() {
        likelihoodKnown = false;
    }

    private final int dimension;
    private final int count;
    private final Parameter datesParameter;
    private final MatrixParameter locationsParameter;
    private final Parameter regressionSlopeParameter;
    private final Parameter regressionPrecisionParameter;
    private final Parameter splitTimeParameter;
    private final Parameter splitAngleParameter;
    private final Parameter splitAssignmentParameter;

    private double earliestDate;
    private double logLikelihood = 0.0;
    private double storedLogLikelihood = 0.0;
    private boolean likelihoodKnown = false;

    // **************************************************************
    // XMLObjectParser
    // **************************************************************

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {

        public final static String LOCATIONS = "locations";
        public final static String DATES = "dates";
        public final static String REGRESSIONSLOPE = "regressionSlope";
        public final static String REGRESSIONPRECISION = "regressionPrecision";
        public final static String SPLITTIME = "splitTime";
        public final static String SPLITANGLE = "splitAngle";
        public final static String SPLITASSIGNMENT = "splitAssignment";

        public String getParserName() {
            return ANTIGENIC_SPLIT_PRIOR;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            MatrixParameter locationsParameter = (MatrixParameter) xo.getElementFirstChild(LOCATIONS);
            Parameter datesParameter = (Parameter) xo.getElementFirstChild(DATES);
            Parameter regressionSlopeParameter = (Parameter) xo.getElementFirstChild(REGRESSIONSLOPE);
            Parameter regressionPrecisionParameter = (Parameter) xo.getElementFirstChild(REGRESSIONPRECISION);
            Parameter splitTimeParameter = (Parameter) xo.getElementFirstChild(SPLITTIME);
            Parameter splitAngleParameter = (Parameter) xo.getElementFirstChild(SPLITANGLE);
            Parameter splitAssignmentParameter = (Parameter) xo.getElementFirstChild(SPLITASSIGNMENT);

            AntigenicSplitPrior AGDP = new AntigenicSplitPrior(
                locationsParameter,
                datesParameter,
                regressionSlopeParameter,
                regressionPrecisionParameter,
                splitTimeParameter,
                splitAngleParameter,
                splitAssignmentParameter);

//            Logger.getLogger("dr.evomodel").info("Using EvolutionaryCartography model. Please cite:\n" + Utils.getCitationString(AGL));

            return AGDP;
        }

        //************************************************************************
        // AbstractXMLObjectParser implementation
        //************************************************************************

        public String getParserDescription() {
            return "Provides the likelihood of a vector of coordinates in some multidimensional 'antigenic' space based on an expected relationship with time.";
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private final XMLSyntaxRule[] rules = {
                new ElementRule(LOCATIONS, MatrixParameter.class),
                new ElementRule(DATES, Parameter.class),
                new ElementRule(REGRESSIONSLOPE, Parameter.class),
                new ElementRule(REGRESSIONPRECISION, Parameter.class),
                new ElementRule(SPLITTIME, Parameter.class),
                new ElementRule(SPLITANGLE, Parameter.class),
                new ElementRule(SPLITASSIGNMENT, Parameter.class)
        };

        public Class getReturnType() {
            return ContinuousAntigenicTraitLikelihood.class;
        }
    };

    public List<Citation> getCitations() {
        List<Citation> citations = new ArrayList<Citation>();
        citations.add(new Citation(
                new Author[]{
                        new Author("T", "Bedford"),
                        new Author("MA", "Suchard"),
                        new Author("P", "Lemey"),
                        new Author("G", "Dudas"),
                        new Author("C", "Russell"),
                        new Author("D", "Smith"),
                        new Author("A", "Rambaut")
                },
                Citation.Status.IN_PREPARATION
        ));
        return citations;
    }
}