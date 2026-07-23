package com.arshraj.vakilconnect.lawyer.entity;

import com.arshraj.vakilconnect.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "specializations")
public class Specialization extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    public Specialization() {
    }

    public Specialization(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
