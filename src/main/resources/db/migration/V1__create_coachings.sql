create table coachings (
  id bigint not null auto_increment primary key,

  status varchar(30) not null,

  video_original_filename varchar(255),
  video_content_type varchar(100),
  video_size_bytes bigint,

  performance_intent text not null,

  ai_provider varchar(50),
  ai_model varchar(100),
  ai_temperature decimal(4, 2),
  ai_prompt_version varchar(50),

  result_scene_intent text,
  result_scene_intent_source varchar(30),

  result_strength_timecode varchar(50),
  result_strength_axis varchar(30),
  result_strength_signal text,
  result_strength_why text,
  result_strength_tier varchar(30),

  result_focus_timecode varchar(50),
  result_focus_axes json,
  result_focus_observed_signal text,
  result_focus_root_cause text,
  result_focus_intent_gap text,
  result_focus_prescription text,

  result_next_step_text text,
  result_next_step_action varchar(50),

  failure_code varchar(100),
  failure_message text,

  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  completed_at datetime(6)
);
