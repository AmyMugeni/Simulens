import pandas as pd

# ---------------------------------------------------------
# 1. Use the existing student dataset
# ---------------------------------------------------------

df_student_activities = df.copy()

# Create a 'timestamp' column from 'session_start' (Unix milliseconds)
df_student_activities["timestamp_utc"] = pd.to_datetime(
    df_student_activities["session_start"],
    unit="ms",
    utc=True
)

df_student_activities["timestamp_local"] = (
    df_student_activities["timestamp_utc"]
    .dt.tz_convert("Africa/Nairobi")
)

# ---------------------------------------------------------
# 2. Make sure timetable times are represented in minutes
# ---------------------------------------------------------

def time_to_minutes(time_str):
    if pd.isna(time_str):
        return None

    hours, minutes = map(int, str(time_str).strip().split(":"))
    return hours * 60 + minutes


df_timetable["start_minute"] = (
    df_timetable["start_time"].apply(time_to_minutes)
)

df_timetable["end_minute"] = (
    df_timetable["end_time"].apply(time_to_minutes)
)

# ---------------------------------------------------------
# 3. Match each session against the shared timetable
# ---------------------------------------------------------

def evaluate_schedule_conflict(timestamp, timetable_df):

    if pd.isna(timestamp):
        return 0

    event_day = timestamp.weekday()

    event_minute = (
        timestamp.hour * 60
        + timestamp.minute
    )

    day_classes = timetable_df[
        timetable_df["day_of_week"] == event_day
    ]

    conflict = day_classes[
        (day_classes["start_minute"] <= event_minute) &
        (event_minute < day_classes["end_minute"])
    ]

    return int(not conflict.empty)


# ---------------------------------------------------------
# 4. Apply the timetable mapping
# ---------------------------------------------------------

df_student_activities["is_schedule_conflict"] = (
    df_student_activities["timestamp_local"].apply(
        lambda ts: evaluate_schedule_conflict(
            ts,
            df_timetable
        )
    )
)
# ---------------------------------------------------------
# 5. Inspect results
# ---------------------------------------------------------

print("Schedule conflict mapping complete.")

print("\nConflict distribution:")
print(
    df_student_activities["is_schedule_conflict"]
    .value_counts()
)

print("\nFirst 10 mapped sessions:")
display(
    df_student_activities[
        [
            "user_id",
            "timestamp_local",
            "day_of_week",
            "session_hour",
            "app_label",
            "duration_minutes",
            "is_schedule_conflict"
        ]
    ].head(10)
)