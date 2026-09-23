-- ============================================
-- USERS
-- ============================================

create table users (
    user_id       uuid         not null,
    username      varchar(255) not null unique,
    password_hash varchar(255) not null,
    role          enum ('ADMIN','CONTESTMASTER','CONTESTANT') not null,
    primary key (user_id)
);


-- ============================================
-- CONTEST
-- ============================================

create table contest (
    contest_id   uuid         not null,
    contest_name varchar(255) not null,
    host_id      uuid         not null,
    start_time   timestamp(6) with time zone not null,
    end_time     timestamp(6) with time zone not null,
    primary key (contest_id)
);


-- ============================================
-- PROBLEM
-- ============================================
-- Identity anchor only. Title, limits, score, statement, solution template
-- and all test cases live in file storage under the problem_id directory.

create table problem (
    problem_id      uuid    not null unique,
    problem_num     integer not null,
    contest_id      uuid    not null,
    test_case_count integer not null,
    primary key (problem_num, contest_id)
);


-- ============================================
-- REGISTRATION
-- ============================================

create table registration (
    contest_id        uuid not null,
    user_id           uuid not null,
    registration_time timestamp(6) with time zone not null,
    primary key (contest_id, user_id)
);


-- ============================================
-- SUBMISSION
-- ============================================
-- received_at is the authoritative submission time: no client-supplied value
-- is ever used for it. client_duration_ms is client-reported telemetry and
-- must never influence a verdict or a ranking.

create table submission (
    submission_id          uuid    not null unique,
    submission_num         integer not null,
    problem_num            integer not null,
    contest_id             uuid    not null,
    user_id                uuid    not null,
    passed_test_case_count integer not null,
    received_at            timestamp(6) with time zone not null,
    client_duration_ms     integer,
    primary key (submission_num, problem_num, contest_id, user_id)
);


-- ============================================
-- FOREIGN KEYS
-- ============================================

alter table if exists contest
   add constraint fk_contest_host
   foreign key (host_id)
   references users
;
alter table if exists problem
   add constraint fk_problem_contest
   foreign key (contest_id)
   references contest
;
alter table if exists registration
   add constraint fk_registration_contest
   foreign key (contest_id)
   references contest
;
alter table if exists registration
   add constraint fk_registration_user
   foreign key (user_id)
   references users
;
alter table if exists submission
   add constraint fk_submission_problem
   foreign key (problem_num, contest_id)
   references problem
;
alter table if exists submission
   add constraint fk_submission_user
   foreign key (user_id)
   references users
;
