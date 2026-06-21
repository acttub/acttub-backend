alter table coachings
  modify column ai_model varchar(200);

alter table coachings
  modify column ai_prompt_version varchar(100);

alter table coachings
  modify column result_scene_intent_source varchar(100);

alter table coachings
  modify column result_strength_timecode varchar(100);

alter table coachings
  modify column result_strength_axis varchar(100);

alter table coachings
  modify column result_strength_tier varchar(100);

alter table coachings
  modify column result_focus_timecode varchar(100);

alter table coachings
  modify column result_next_step_action varchar(100);

alter table coaching_focus_axes
  modify column axis varchar(100) not null;
