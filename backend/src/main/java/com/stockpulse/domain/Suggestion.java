package com.stockpulse.domain;
public final class Suggestion {
    private Suggestion() { }
    public enum Status { PENDING, ACCEPTED, REJECTED, FAILED, EXPIRED }
    public enum Trigger { INITIAL, INVENTORY_LOW, DEMAND_SPIKE, MANUAL }
    public enum Direction { INCREASE, DECREASE, HOLD }
    public enum ReviewDecision { ACCEPT, REJECT }
}
