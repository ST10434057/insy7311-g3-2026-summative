# Emeris StudyTrack

Emeris StudyTrack is a high-fidelity Android prototype for Emeris students who need to organise academic tasks, prioritise deadlines and create a realistic daily study plan.

## Main features

- Add and edit tasks with persistent labels, constrained dropdowns and a future-date picker.
- Automatic priority score based on assessment type, urgency, difficulty and estimated effort.
- Search, filter and sort all tasks.
- Log planned study time against each task, view original/studied/remaining time, and only complete a task when no time remains.
- Manually complete, return to pending, edit and delete tasks with undo feedback.
- Generate a daily plan that fits into 2, 4, 6 or 8 available study hours and remember how much of today’s session has been logged.
- Monitor overall completion, high-priority work, streaks and the last seven days of activity.
- Local persistence using SharedPreferences and JSON.
- Material 3 visual system, text labels, 48dp controls, colour-plus-text status indicators and content descriptions.

## Navigation model

The bottom navigation contains four top-level destinations: Home, Tasks, Today and Progress. Adding a task is an action, not a destination, and opens a secondary screen with a back button.

