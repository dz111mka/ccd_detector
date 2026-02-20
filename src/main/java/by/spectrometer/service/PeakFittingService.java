package by.spectrometer.service;

import by.spectrometer.model.Peak;

public class PeakFittingService {

    public interface PeakFunction {
        double evaluate(double x, double[] params);
        double[] getInitialParameters(double[] data, int peakPixel);
    }

    public static class GaussianFunction implements PeakFunction {
        @Override
        public double evaluate(double x, double[] params) {
            double a = params[0]; // Amplitude
            double x0 = params[1]; // Peak center
            double sigma = params[2]; // Width
            double b = params[3]; // Baseline
            
            return a * Math.exp(-Math.pow(x - x0, 2) / (2 * Math.pow(sigma, 2))) + b;
        }

        @Override
        public double[] getInitialParameters(double[] data, int peakPixel) {
            double a = data[peakPixel];
            double x0 = peakPixel;
            double sigma = 10;
            double b = 0;
            
            return new double[]{a, x0, sigma, b};
        }
    }

    public static class LorentzianFunction implements PeakFunction {
        @Override
        public double evaluate(double x, double[] params) {
            double a = params[0]; // Amplitude
            double x0 = params[1]; // Peak center
            double gamma = params[2]; // Width
            double b = params[3]; // Baseline
            
            return a / (1 + Math.pow((x - x0) / gamma, 2)) + b;
        }

        @Override
        public double[] getInitialParameters(double[] data, int peakPixel) {
            double a = data[peakPixel];
            double x0 = peakPixel;
            double gamma = 10;
            double b = 0;
            
            return new double[]{a, x0, gamma, b};
        }
    }

    public double[] fitPeak(double[] data, int peakPixel, PeakFunction function, int windowSize) {
        int start = Math.max(0, peakPixel - windowSize);
        int end = Math.min(data.length - 1, peakPixel + windowSize);
        
        double[] initialParams = function.getInitialParameters(data, peakPixel);
        double[] bestParams = initialParams.clone();
        
        double minError = Double.MAX_VALUE;
        
        // Simple grid search for optimization
        for (double sigma = 5; sigma <= 20; sigma += 0.5) {
            for (double amplitude = 0.5 * initialParams[0]; amplitude <= 1.5 * initialParams[0]; amplitude += initialParams[0] * 0.1) {
                double[] params = {amplitude, peakPixel, sigma, initialParams[3]};
                double error = calculateFittingError(data, start, end, function, params);
                
                if (error < minError) {
                    minError = error;
                    bestParams = params.clone();
                }
            }
        }
        
        return bestParams;
    }

    private double calculateFittingError(double[] data, int start, int end, PeakFunction function, double[] params) {
        double error = 0;
        
        for (int i = start; i <= end; i++) {
            double predicted = function.evaluate(i, params);
            double actual = data[i];
            error += Math.pow(predicted - actual, 2);
        }
        
        return error;
    }

    public double[] generateFittedPeak(double[] data, int peakPixel, PeakFunction function, int windowSize) {
        int start = Math.max(0, peakPixel - windowSize);
        int end = Math.min(data.length - 1, peakPixel + windowSize);
        
        double[] params = fitPeak(data, peakPixel, function, windowSize);
        double[] fitted = new double[data.length];
        
        for (int i = 0; i < data.length; i++) {
            if (i >= start && i <= end) {
                fitted[i] = function.evaluate(i, params);
            } else {
                fitted[i] = 0;
            }
        }
        
        return fitted;
    }
}