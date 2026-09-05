create table contest (
    end_time timestamp(6) not null,
    start_time timestamp(6) not null,
    contest_id uuid not null,
    host_id uuid not null,
    contest_name varchar(255) not null,
    primary key (contest_id)
);

create table problem (
    problem_num integer not null,
    test_case_count integer not null,
    contest_contest_id uuid not null,
    primary key (problem_num, contest_contest_id)
);

create table registration (
    registration_time timestamp(6) not null,
    contest_contest_id uuid not null,
    user_user_id uuid not null,
    primary key (contest_contest_id, user_user_id)
);

create table submission (
    passed_test_case_count integer not null,
    problem_problem_num integer not null,
    submission_num integer not null,
    problem_contest_contest_id uuid not null,
    user_user_id uuid not null,
    primary key (problem_problem_num, submission_num, problem_contest_contest_id, user_user_id)
);

create table users (
    user_id uuid not null,
    password_hash varchar(255) not null,
    username varchar(255) not null unique,
    role enum ('ADMIN','HOST','USER') not null,
    primary key (user_id)
);

alter table if exists problem
   add constraint one_contest_many_problem
   foreign key (contest_contest_id)
   references contest
;
alter table if exists registration
   add constraint one_contest_many_registration
   foreign key (contest_contest_id)
   references contest
;
alter table if exists registration
   add constraint one_user_many_registration
   foreign key (user_user_id)
   references users
;
alter table if exists submission
   add constraint one_problem_many_submission
   foreign key (problem_problem_num, problem_contest_contest_id)
   references problem
;
alter table if exists submission
   add constraint one_user_many_submission
   foreign key (user_user_id)
   references users
;