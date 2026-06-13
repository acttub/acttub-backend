create table coaching_evaluations (
  id bigint not null auto_increment primary key,
  coaching_id bigint not null,
  rating int not null,
  comment text,
  created_at datetime(6) not null,

  constraint fk_coaching_evaluations_coaching
    foreign key (coaching_id) references coachings(id),

  constraint uq_coaching_evaluations_coaching
    unique (coaching_id),

  constraint chk_coaching_evaluations_rating
    check (rating between 1 and 5)
);
