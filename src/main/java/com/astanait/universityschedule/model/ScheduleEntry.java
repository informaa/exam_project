package com.astanait.universityschedule.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor

// класс который будет представлять одну запись в расписании
public class ScheduleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String subjectName;

    @Column(length = 100)
    private String teacherName;

    @Column(length = 50)
    private String room;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(length = 10)
    private String academicYear;

    @Column
    private Integer semester;

    @Column
    private Integer weekNumber;
}