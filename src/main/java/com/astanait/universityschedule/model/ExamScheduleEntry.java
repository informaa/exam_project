package com.astanait.universityschedule.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "exam_schedule_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamScheduleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer academicYear;

    @Column(nullable = false)
    private Integer academicPeriod;

    @Column(nullable = false)
    private LocalDate examDate;

    @Column(length = 20)
    private String dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 255)
    private String disciplineName;

    @Column(length = 255)
    private String examinerName;

    @Column(length = 100)
    private String controlForm;

    @Column(length = 100)
    private String building;

    @Column(length = 50)
    private String room;

    @Column(columnDefinition = "TEXT")
    private String additionalInfo;

    @Column(name = "group_name", length = 50, nullable = false)
    private String groupName;
}