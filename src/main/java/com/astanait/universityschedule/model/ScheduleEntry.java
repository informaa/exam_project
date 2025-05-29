package com.astanait.universityschedule.model;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.util.Objects;

@Entity
public class ScheduleEntry {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    private int lessonNumber;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;

    private String academicYear;
    private int semester;
    private int weekInSemester;

    public ScheduleEntry() {}

    public ScheduleEntry(DayOfWeek dayOfWeek, int lessonNumber, Group group, Subject subject, Room room,
                         String academicYear, int semester, int weekInSemester) {
        this.dayOfWeek = dayOfWeek;
        this.lessonNumber = lessonNumber;
        this.group = group;
        this.subject = subject;
        this.room = room;
        this.academicYear = academicYear;
        this.semester = semester;
        this.weekInSemester = weekInSemester;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public int getLessonNumber() { return lessonNumber; }
    public Group getGroup() { return group; }
    public void setGroup(Group group) { this.group = group; }
    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduleEntry that = (ScheduleEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}