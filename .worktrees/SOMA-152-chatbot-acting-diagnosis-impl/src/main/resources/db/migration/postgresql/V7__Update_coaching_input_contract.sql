alter table coachings
  add column genre text;

alter table coachings
  add column custom_genre text;

alter table coachings
  add column situation text;

alter table coachings
  add column character_setting text;

alter table coachings
  add column subtext text;

alter table coachings
  alter column performance_intent drop not null;
