import pandas as pd
import numpy as np

# 1. Load Primary Dataset from the current working directory (which is your Drive folder)
df_student_activities = pd.read_csv("SocialMediaUse_export (4).csv")

cols_to_drop = [
    'notification_count', 'triggered_by_notification',
    'scroll_count', 'scroll_speed_estimate', 'avg_scroll_delta_px',
    'micro_scrolls', 'normal_scrolls', 'macro_scrolls',
    'scroll_behavior', 'detected_format'
]
df_student_activities = df.drop(columns=[c for c in cols_to_drop if c in df.columns], errors='ignore')

print("Initial data loaded and cleaned.")
display(df_student_activities.head())

# Save the DataFrame to a new CSV file
output_filename = "student_activities_with_conflicts.csv"
df_student_activities.to_csv(output_filename, index=False)

print(f"File '{output_filename}' saved successfully. Downloading...")

# Trigger the download
files.download(output_filename)