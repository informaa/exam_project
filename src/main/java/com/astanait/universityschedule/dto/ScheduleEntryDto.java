package com.astanait.universityschedule.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleEntryDto {

    private Long id;

    @NotBlank(message = "Название предмета не может быть пустым")
    @Size(max = 100, message = "Название предмета не должно превышать 100 символов")
    private String subjectName;

    @Size(max = 100, message = "Имя преподавателя не должно превышать 100 символов")
    private String teacherName;

    @Size(max = 50, message = "Название аудитории не должно превышать 50 символов")
    private String room;

    @NotNull(message = "Время начала не может быть пустым")
    private LocalDateTime startTime;

    @NotNull(message = "Время окончания не может быть пустым")
    private LocalDateTime endTime;

    @NotBlank(message = "Академический год не может быть пустым")
    @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "Формат года должен быть ГГГГ-ГГГГ")
    private String academicYear;

    @NotNull(message = "Семестр не может быть пустым")
    @Min(value = 1, message = "Номер семестра должен быть 1 или 2")
    @Max(value = 2, message = "Номер семестра должен быть 1 или 2")
    private Integer semester;

    @NotNull(message = "Номер недели не может быть пустым")
    @Min(value = 1, message = "Номер недели должен быть не меньше 1")
    @Max(value = 15, message = "Номер недели не должен превышать 15")
    private Integer weekNumber;

    @AssertTrue(message = "Время окончания должно быть после времени начала")
    public boolean isEndTimeAfterStartTime() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}