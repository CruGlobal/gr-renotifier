select _id

from designation
 -- siebel is 8194da28-fbc9-11e3-bd50-12543788cf06
where _system_id = '8194da28-fbc9-11e3-bd50-12543788cf06'
and _updated_at > '2019-02-20 00:00:00'

order by _updated_at desc
limit 1000;
