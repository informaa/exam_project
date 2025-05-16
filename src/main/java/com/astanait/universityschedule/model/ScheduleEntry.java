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

    @Column(length = 10, nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private Integer semester;

    @Column(nullable = false)
    private Integer weekNumber;

    @Column(name = "group_name", length = 50, nullable = false)
    private String groupName;

    @Column(name = "subject_type", length = 30, nullable = false)
    private String subjectType;
}