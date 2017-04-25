package com.example.models;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import net.corda.core.node.PhysicalLocation;

/**
 * Created by evilkid on 4/24/2017.
 */
@JsonDeserialize
public class PeerInfo {
    private String name;
    private PhysicalLocation location;

    public PeerInfo(String name, PhysicalLocation location) {
        this.name = name;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PhysicalLocation getLocation() {
        return location;
    }

    public void setLocation(PhysicalLocation location) {
        this.location = location;
    }
}
