package com.example.models;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import net.corda.core.messaging.SingleMessageRecipient;
import net.corda.core.node.PhysicalLocation;
import net.corda.core.node.ServiceEntry;

import java.util.List;

/**
 * Created by evilkid on 4/24/2017.
 */
@JsonDeserialize
public class PeerInfo {
    private String name;
    private SingleMessageRecipient address;
    private PhysicalLocation location;
    private List<ServiceEntry> advertisedServices;

    public PeerInfo(String name, SingleMessageRecipient address, PhysicalLocation location, List<ServiceEntry> advertisedServices) {
        this.name = name;
        this.address = address;
        this.location = location;
        this.advertisedServices = advertisedServices;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SingleMessageRecipient getAddress() {
        return address;
    }

    public void setAddress(SingleMessageRecipient address) {
        this.address = address;
    }

    public PhysicalLocation getLocation() {
        return location;
    }

    public void setLocation(PhysicalLocation location) {
        this.location = location;
    }

    public List<ServiceEntry> getAdvertisedServices() {
        return advertisedServices;
    }

    public void setAdvertisedServices(List<ServiceEntry> advertisedServices) {
        this.advertisedServices = advertisedServices;
    }
}
