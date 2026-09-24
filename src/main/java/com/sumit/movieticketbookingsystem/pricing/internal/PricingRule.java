package com.sumit.movieticketbookingsystem.pricing.internal;

/**
 * One step of the price pipeline. Rules run in their Order: tier price (10), day-of-week (20),
 * discount (30), convenience fee (40), GST (50).
 */
interface PricingRule {

    void apply(PricingContext context);
}
