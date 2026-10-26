#!/bin/bash
# Two simultaneous sessions allocate into one obligation: the second must be rejected and totals stay consistent.
# Run AFTER test_rbac.sql (needs its fixtures) on the same database.
DB=${1:-campx_test}; A=aaaaaaaa-0000-0000-0000-000000000001; ACCT=a1000000-0000-0000-0000-000000000013; P1=9a000000-0000-0000-0000-000000000001; P2=9a000000-0000-0000-0000-000000000002; OB1=0b000000-0000-0000-0000-000000000001
psql_() { su postgres -c "psql -q -At -d $DB -c \"$1\"" 2>&1 | grep -vE '^$' | tail -2; }
psql_ "insert into fin.payments (id,tenant_id,student_id,payment_no,amount,status) values ('$P2','$A','5d000000-0000-0000-0000-000000000001','P2',1000,'SUCCESS') on conflict do nothing"
run() { psql_ "begin; set local role authenticated; select t.jwt('$ACCT','$A'); select fin.allocate_payment('$1','$OB1',$2); select pg_sleep($3); commit;"; }
reset() { psql_ "truncate fin.payment_allocations; begin; set local campx.allow_derived='on'; update fin.fee_obligations set paid_amount=0, status='OPEN' where obligation_no='OB1'; commit;"; }
show() { su postgres -c "psql -At -d $DB -c \"select 'allocations='||(select count(*) from fin.payment_allocations)||' obligation paid='||paid_amount||' status='||status from fin.fee_obligations where obligation_no='OB1'\""; }
echo "== two DIFFERENT payments, 600 each, into a 1000 obligation, at the same time"; reset
( run $P1 600 3 > /tmp/c1.out 2>&1 & ); sleep 1; run $P2 600 0 > /tmp/c2.out 2>&1; sleep 3
echo "session1: $(tr '\n' ' ' </tmp/c1.out | cut -c1-90)"; echo "session2: $(tr '\n' ' ' </tmp/c2.out | cut -c1-200)"; show
echo "== SAME payment (1000), 700 twice at the same time"; reset
( run $P1 700 3 > /tmp/c3.out 2>&1 & ); sleep 1; run $P1 700 0 > /tmp/c4.out 2>&1; sleep 3
echo "session1: $(tr '\n' ' ' </tmp/c3.out | cut -c1-90)"; echo "session2: $(tr '\n' ' ' </tmp/c4.out | cut -c1-200)"; show
reset
