
/*  
	The current simple map refset delta file is an accurate derivative of the current full file
*/

/* view of current snapshot, derived from current full */
	drop table if exists v_temp_view;
 	create table if not exists v_temp_view like curr_simplemaprefset_f;
 	insert into v_temp_view
	select a.*
	from curr_simplemaprefset_f a
	where a.effectivetime = '<CURRENT-RELEASE-DATE>';

/* in the delta; not in the full */
	insert into qa_result (runid, assertionuuid, assertiontext, details)
	select 
		<RUNID>,
		'<ASSERTIONUUID>',
		'<ASSERTIONTEXT>',
		concat('SIMPLE MAP REFSET: id=',a.id, ': Member is in DELTA file, but not in FULL file.')
	from curr_simplemaprefset_d a
	left join v_temp_view b
	on a.id = b.id
	and a.effectivetime = b.effectivetime
	and a.active = b.active
    and a.moduleid = b.moduleid
    and a.refsetid = b.refsetid
    and a.referencedcomponentid = b.referencedcomponentid
    and a.maptarget = b.maptarget
	where b.id is null
	or b.effectivetime is null
	or b.active is null
	or b.moduleid is null
  	or b.refsetid is null
  	or b.referencedcomponentid is null
  	or b.maptarget is null;

/* in the full; not in the delta */
	insert into qa_result (runid, assertionuuid, assertiontext, details)
	select 
		<RUNID>,
		'<ASSERTIONUUID>',
		'<ASSERTIONTEXT>',
		concat('SIMPLE MAP REFSET: id=',a.id, ': Member is in FULL file, but not in DELTA file.') 
	from v_temp_view a
	left join curr_simplemaprefset_d b 
	on a.id = b.id
	and a.effectivetime = b.effectivetime
	and a.active = b.active
    and a.moduleid = b.moduleid
    and a.refsetid = b.refsetid
    and a.referencedcomponentid = b.referencedcomponentid
    and a.maptarget = b.maptarget
	where b.id is null
	or b.effectivetime is null
	or b.active is null
	or b.moduleid is null
  	or b.refsetid is null
  	or b.referencedcomponentid is null
  	or b.maptarget is null;
	
	commit;
	drop table if exists v_temp_view;






