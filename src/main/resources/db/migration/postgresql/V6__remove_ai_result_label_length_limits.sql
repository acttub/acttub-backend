alter table coachings
  alter column result_scene_intent_source type text;

alter table coachings
  alter column result_strength_timecode type text;

alter table coachings
  alter column result_strength_axis type text;

alter table coachings
  alter column result_strength_tier type text;

alter table coachings
  alter column result_focus_timecode type text;

alter table coachings
  alter column result_next_step_action type text;

alter table coaching_focus_axes
  drop constraint uq_coaching_focus_axes_axis;

alter table coaching_focus_axes
  alter column axis type text;
