alter table coachings
  modify column result_scene_intent_source text;

alter table coachings
  modify column result_strength_timecode text;

alter table coachings
  modify column result_strength_axis text;

alter table coachings
  modify column result_strength_tier text;

alter table coachings
  modify column result_focus_timecode text;

alter table coachings
  modify column result_next_step_action text;

alter table coaching_focus_axes
  drop index uq_coaching_focus_axes_axis;

alter table coaching_focus_axes
  modify column axis text not null;
