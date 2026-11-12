-- ============================================
-- Verdicts, language, and source code on submissions
-- ============================================
--
-- verdict and message are the server's judgement. source_code is kept only for
-- a contestant's latest attempt at each problem; older attempts have it
-- cleared. compiler_output holds diagnostics for a compilation error.

alter table submission add column verdict         varchar(32);
alter table submission add column message         varchar(255);
alter table submission add column language        varchar(16);
alter table submission add column source_code     varchar(65536);
alter table submission add column compiler_output varchar(8192);

-- Submissions graded before verdicts existed: a full pass is the only verdict
-- that can be recovered from the count alone.
update submission set
    verdict = case
        when passed_test_case_count > 0 and passed_test_case_count = (
            select p.test_case_count from problem p
            where p.contest_id = submission.contest_id
              and p.problem_num = submission.problem_num)
        then 'ACCEPTED'
        else 'WRONG_ANSWER'
    end,
    message = 'Graded before verdicts were recorded',
    language = 'unknown';

alter table submission alter column verdict set not null;
alter table submission alter column message set not null;
alter table submission alter column language set not null;
