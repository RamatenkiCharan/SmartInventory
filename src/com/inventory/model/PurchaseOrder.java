package com.inventory.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PurchaseOrder implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Status { PENDING, RECEIVED, CANCELLED }

    private final String poNo, supplierId;
    private final LocalDate createdOn;
    private LocalDate receivedOn;
    private final List<POLine> lines = new ArrayList<>();
    private Status status = Status.PENDING;

    public PurchaseOrder(String poNo, String supplierId) { this.poNo = poNo; this.supplierId = supplierId; this.createdOn = LocalDate.now(); }

    /** Rebuilds a stored order (used when loading from the database). */
    public PurchaseOrder(String poNo, String supplierId, LocalDate createdOn, LocalDate receivedOn, Status status) {
        this.poNo = poNo; this.supplierId = supplierId; this.createdOn = createdOn;
        this.receivedOn = receivedOn; this.status = status;
    }

    public void addLine(POLine l)      { lines.add(l); }
    public List<POLine> getLines()     { return Collections.unmodifiableList(lines); }
    public String getPoNo()            { return poNo; }
    public String getSupplierId()      { return supplierId; }
    public Status getStatus()          { return status; }
    public LocalDate getCreatedOn()    { return createdOn; }
    public LocalDate getReceivedOn()   { return receivedOn; }
    public void markReceived()         { status = Status.RECEIVED; receivedOn = LocalDate.now(); }
    public void markCancelled()        { status = Status.CANCELLED; }
    public double total()              { return lines.stream().mapToDouble(POLine::total).sum(); }
}
