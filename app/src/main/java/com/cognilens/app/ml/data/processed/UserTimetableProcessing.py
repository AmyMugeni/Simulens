from google.colab import files
import pdfplumber
import re
import pandas as pd
from datetime import datetime
rows = [
    ["Monday", "08:15", "10:15", "ICS 4106",
     "Computer Simulation and Modelling", "C Ouma", "STM-B F2-05"],
    ["Monday", "10:15", "12:15", "ICS 4102",
     "Machine Learning", "J Wekesa", ""],
    ["Monday", "13:15", "14:15", "ICS 4103",
     "Cloud Computing", "V Ochango", ""],
    ["Monday", "15:15", "17:15", "ICS 3105",
     "Multimedia Applications", "Z Muti", ""],

    ["Tuesday", "08:15", "10:15", "ICS 4101",
     "ICS Project II", "K Omondi", ""],
    ["Tuesday", "10:15", "11:15", "",
     "Maisha Program 7 - Gents 4A", "P Neri", "RM B"],
    ["Tuesday", "11:15", "12:15", "",
     "Maisha Program 7 - Ladies ICS 4A", "R Bunde", "RM 10"],
    ["Tuesday", "13:15", "15:15", "ICS 4104",
     "Distributed Systems", "H Talo", "STM-B F2-05"],

    ["Wednesday", "08:15", "10:15", "ICS 4111",
     "Embedded Systems and Internet of Things", "A Vikiru", ""],
    ["Wednesday", "11:15", "12:15", "ICS 4106",
     "Computer Simulation and Modelling", "C Ouma", ""],
    ["Wednesday", "13:15", "15:15", "ICS 4104",
     "Distributed Systems", "H Talo", ""],
    ["Wednesday", "15:15", "17:15", "ICS 4103",
     "Cloud Computing", "V Ochango", "MSB 13"],

    ["Thursday", "08:15", "10:15", "ICS 4101",
     "ICS Project II", "K Omondi", "LT 6"],
    ["Thursday", "11:15", "13:15", "ICS 4111",
     "Embedded Systems and Internet of Things", "A Vikiru", "Masinga Lab"],
    ["Thursday", "15:15", "17:15", "ICS 3105",
     "Multimedia Applications", "Z Muti", "LT 6"],

    ["Friday", "08:15", "10:15", "ICS 4102",
     "Machine Learning", "J Wekesa", "LT 2"],
    ["Friday", "14:15", "17:15", "",
     "Sports/Clubs", "", ""],
]

columns = [
    "day", "start_time", "end_time",
    "unit_code", "unit_name", "lecturer", "venue"
]

timetable_df = pd.DataFrame(rows, columns=columns)

# Create an 'activity' column by combining 'unit_code' and 'unit_name'
def get_activity_name(row):
    if row['unit_code'].strip():
        if row['unit_name'].strip():
            return f"{row['unit_code']} - {row['unit_name']}"
        else:
            return row['unit_code']
    else:
        return row['unit_name']

timetable_df['activity'] = timetable_df.apply(get_activity_name, axis=1)

# put weekdays in calendar order
weekday_order = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]
timetable_df["day"] = pd.Categorical(
    timetable_df["day"],
    categories=weekday_order,
    ordered=True
)
timetable_df = timetable_df.sort_values(["day", "start_time"])

# Select only the desired 4 columns for the final CSV
df_final_csv = timetable_df[["day", "start_time", "end_time", "activity"]]

# Save the structured timetable
df_final_csv.to_csv("bics_4a_apr_jul_2026.csv", index=False)

# 1. Load your generated timetable CSV
df_timetable = pd.read_csv("bics_4a_apr_jul_2026.csv")

# 2. Convert string time "HH:MM" to 24-hour decimal hours (e.g., "08:15" -> 8.25)
def time_to_decimal(time_str):
    if pd.isna(time_str):
        return np.nan
    hours, minutes = map(int, str(time_str).strip().split(":"))
    return hours + (minutes / 60.0)

df_timetable["start_decimal"] = df_timetable["start_time"].apply(time_to_decimal)
df_timetable["end_decimal"] = df_timetable["end_time"].apply(time_to_decimal)

# 3. Map day strings to standard numerical weekdays (Monday=0, Tuesday=1, ..., Sunday=6)
day_mapping = {
    "Monday": 0, "Tuesday": 1, "Wednesday": 2,
    "Thursday": 3, "Friday": 4, "Saturday": 5, "Sunday": 6
}
df_timetable["day_of_week"] = df_timetable["day"].map(day_mapping)

# 4. Binary Indicator: Flag scheduled commitments
df_timetable["is_scheduled_commitment"] = 1

print("=== PROCESSED TIMETABLE FEATURE MAP ===")
display(df_timetable[["day", "day_of_week", "start_decimal", "end_decimal", "activity", "is_scheduled_commitment"]])