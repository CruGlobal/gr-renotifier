select _id

from designation
 -- siebel is 8194da28-fbc9-11e3-bd50-12543788cf06
where _system_id = '8194da28-fbc9-11e3-bd50-12543788cf06'
and _updated_at > '2019-02-01 00:00:00'
--  and status = 'Active'
--  and secure_flag = true

order by _updated_at asc;
