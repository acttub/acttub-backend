alter table coachings
  drop constraint if exists coachings_status_check;

alter table coachings
  add constraint coachings_status_check
    check (status in (
      'ANALYZING',
      'QUESTION_READY',
      'IN_CONVERSATION',
      'READY_FOR_DIAGNOSIS',
      'COMPLETED',
      'FAILED'
    ));
