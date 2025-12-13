package com.dealaggregator.dealapi.service;

import org.springframework.stereotype.Service;

@Service
public class BlackScholesService {

    // General Black-Scholes method for both call and put options
    public double blackScholes(double s, double k, double t, double v, double r, String optionType) {
        // Calculate d1 and d2
        double d1 = (Math.log(s / k) + (r + 0.5 * Math.pow(v, 2)) * t) / (v * Math.sqrt(t));
        double d2 = d1 - v * Math.sqrt(t);

        double price;
        if ("call".equalsIgnoreCase(optionType)) {
            // Call option: S * N(d1) - K * e^(-rt) * N(d2)
            price = s * cumulativeDistribution(d1) - k * Math.exp(-r * t) * cumulativeDistribution(d2);
        } else {
            // Put option: K * e^(-rt) * N(-d2) - S * N(-d1)
            price = k * Math.exp(-r * t) * cumulativeDistribution(-d2) - s * cumulativeDistribution(-d1);
        }

        return Math.round(price * 100.0) / 100.0;
    }

    private double cumulativeDistribution(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }
    // Error Function Approximation (required for CDF)
    private double erf(double z) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(z));
        
        // Horner's method approximation for error function
        double ans = 1 - t * Math.exp(-z * z - 1.26551223 +
                t * (1.00002368 +
                t * (0.37409196 +
                t * (0.09678418 +
                t * (-0.18628806 +
                t * (0.27886807 +
                t * (-1.13520398 +
                t * (1.48851587 +
                t * (-0.82215223 +
                t * 0.17087277)))))))));
        if (z >= 0) return ans;
        else return -ans;
    }
    
}
