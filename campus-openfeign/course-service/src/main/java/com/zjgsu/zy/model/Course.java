package com.zjgsu.zy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int selectedCount;

    public int getSelectedCount() { return selectedCount; }
    public int getRemainingSeats() { return capacity - selectedCount; }

    protected Course() {
    }

    public Course(String code, String name, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("璇剧▼瀹归噺蹇呴』澶т簬 0");
        }
        this.code = code;
        this.name = name;
        this.capacity = capacity;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }
}