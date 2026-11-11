import io
import os
import pdfplumber
import pandas as pd
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(
    title="Timetable Parser API",
    description="Extracts and grids Strathmore style PDF timetables by coordinate mapping",
    version="1.0.0"
)

# Enable CORS so your Android app or emulators can query the backend cleanly
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

time_slots = [
    "8.15 - 9.15", "9.15 – 10.15", "10.15 – 11.15", "11.15 – 12.15",
    "12.15 - 1.15", "1.15 - 2.15", "2.15 – 3.15", "3.15 – 4.15", "4.15 - 5.15"
]
x_bounds = [99.0, 165.1, 224.1, 298.8, 377.3, 448.0, 514.9, 586.6, 664.1, 741.9]

def get_col_idx(x_mid: float) -> int:
    for i in range(len(x_bounds) - 1):
        if x_bounds[i] <= x_mid < x_bounds[i+1]:
            return i
    return -1

def get_day_of_week(y: float) -> str:
    if 80 <= y < 150: return "Monday"
    if 150 <= y < 220: return "Tuesday"
    if 220 <= y < 296: return "Wednesday"
    if 296 <= y < 373: return "Thursday"
    if 373 <= y < 460: return "Friday"
    return None

@app.get("/")
def home():
    return {"status": "success", "message": "Timetable Parser API is active!"}

@app.post("/parse-timetable")
async def parse_timetable(file: UploadFile = File(...)):
    if not file.filename.lower().endswith('.pdf'):
        raise HTTPException(status_code=400, detail="Invalid file type. Please upload a PDF.")

    try:
        pdf_content = await file.read()
        pdf_stream = io.BytesIO(pdf_content)

        with pdfplumber.open(pdf_stream) as pdf:
            if len(pdf.pages) == 0:
                raise HTTPException(status_code=400, detail="The uploaded PDF file has no pages.")
            page = pdf.pages[0]
            words = page.extract_words(x_tolerance=3, y_tolerance=3)

        schedule = {day: {slot: [] for slot in time_slots} for day in ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]}

        for w in words:
            day = get_day_of_week(w['top'])
            if not day:
                continue

            x_mid = (w['x0'] + w['x1']) / 2
            col_idx = get_col_idx(x_mid)
            if col_idx == -1:
                continue

            slot = time_slots[col_idx]
            schedule[day][slot].append(w)

        parsed_timetable = []
        for day in ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]:
            row_data = {"day": day}
            for slot in time_slots:
                cell_words = schedule[day][slot]
                if not cell_words:
                    row_data[slot] = "-"
                    continue

                sorted_words = sorted(cell_words, key=lambda x: (round(x['top'] / 3) * 3, x['x0']))

                lines = []
                curr_line = []
                last_top = None
                for w in sorted_words:
                    if last_top is None or abs(w['top'] - last_top) < 4:
                        curr_line.append(w['text'])
                    else:
                        lines.append(" ".join(curr_line))
                        curr_line = [w['text']]
                    last_top = w['top']
                if curr_line:
                    lines.append(" ".join(curr_line))

                row_data[slot] = " | ".join(lines)
            parsed_timetable.append(row_data)

        return {
            "status": "success",
            "filename": file.filename,
            "timetable": parsed_timetable
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"An error occurred while parsing the file: {str(e)}")
