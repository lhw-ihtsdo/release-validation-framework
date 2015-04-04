
/*  
	The current text definition delta file is an accurate derivative of the current full file
*/

/* view of current delta, derived from current full */
  drop table if exists v_temp_view;
  create table if not exists v_temp_view like curr_textdefinition_f;
  insert into v_temp_view
	select a.*
	from curr_textdefinition_f a
	where cast(effectivetime as datetime) >
			(select max(cast(effectivetime as datetime)) 
			 from prev_textdefinition_f);

/* in the delta; not in the full */
	insert into qa_result (runid, assertionuuid, assertiontext, details)
	select 
	<RUNID>,
	'<ASSERTIONUUID>',
	'<ASSERTIONTEXT>',
	concat('Definition: id=',a.id, ' is in DELTA file, but not in FULL file.') 	
	from curr_textdefinition_d a
	left join v_temp_view b
	on a.id = b.id
	and a.effectivetime = b.effectivetime
	and a.active = b.active
	and a.moduleid = b.moduleid
	and a.conceptid = b.conceptid
	and a.languagecode = b.languagecode
	and a.typeid = b.typeid
	and a.term = b.term
	and a.casesignificanceid = b.casesignificanceid
	where b.id is null
	or b.effectivetime is null
	or b.active is null
	or b.moduleid is null
	or b.conceptid is null
	or b.languagecode is null
	or b.typeid is null
	or b.term is null
	or b.casesignificanceid is null;

/* in the full; not in the delta */
	insert into qa_result (runid, assertionuuid, assertiontext, details)
	select 
	<RUNID>,
	'<ASSERTIONUUID>',
	'<ASSERTIONTEXT>',
	concat('Definition: id=',a.id, ' is in FULL file, but not in DELTA file.') 
	from v_temp_view a
	left join curr_textdefinition_d b 
	on a.id = b.id
	and a.effectivetime = b.effectivetime
	and a.active = b.active
	and a.moduleid = b.moduleid
	and a.conceptid = b.conceptid
	and a.languagecode = b.languagecode
	and a.typeid = b.typeid
	and a.term = b.term
	and a.casesignificanceid = b.casesignificanceid
	where b.id is null
	or b.effectivetime is null
	or b.active is null
	or b.moduleid is null
	or b.conceptid is null
	or b.languagecode is null
	or b.typeid is null
	or b.term is null
	or b.casesignificanceid is null;

commit;
drop table if exists v_temp_view;
