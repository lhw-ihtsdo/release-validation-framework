/******************************************************************************** 
	component-centric-snapshot-description-unique-terms

	Assertion:
	Active terms associated with active concepts are unique within hierarchy.

********************************************************************************/

drop table if exists temp_active_fsn_hierarchy;
	create table if not exists temp_active_fsn_hierarchy as
	select distinct a.conceptid, a.languagecode, concat('(',substring_index(a.term, '(', -1)) as semantictag
	from curr_description_s a
		join curr_description_d  b
			on a.conceptid = b.conceptid
			and a.active = 1
			and a.typeid = '900000000000003001' /* fully specified name */
		join curr_concept_s c
			on c.id = b.conceptid
			and c.active = 1;
	commit;	 
	
	alter table temp_active_fsn_hierarchy add index idx_tmp_afh_cid (conceptId);
	alter table temp_active_fsn_hierarchy add index idx_tmp_afh_l (languagecode);
	alter table temp_active_fsn_hierarchy add index idx_tmp_afh_st (semantictag);

/* 	a list of descriptions and their hierarchies */
	drop table if exists tmp_description_syn;
	create table if not exists tmp_description_syn
	select a.id, a.languagecode, a.conceptid, a.term, b.semantictag as semantictag
	from curr_description_s a
	join temp_active_fsn_hierarchy b
	on a.conceptid = b.conceptid
	and a.active = 1
	and a.languagecode = b.languagecode
	where a.typeid = '900000000000013009'; /* syn */
	commit;
	
	alter table tmp_description_syn add index idx_tmp_ds_cid (conceptId);
	alter table tmp_description_syn add index idx_tmp_ds_l (languagecode);
	alter table tmp_description_syn add index idx_tmp_ds_st (semantictag);
	alter table tmp_description_syn add index idx_tmp_ds_t (term);

/* 	violators to the results table */	
	insert into qa_result (runid, assertionuuid, concept_id, details)
	select 
		<RUNID>,
		'<ASSERTIONUUID>',
		a.conceptid,
		concat(cast(a.term as binary), ' non-unique term within hierarchy ', a.semantictag)
	from tmp_description_syn a,
	(select a.term from tmp_description_syn a 
		group by binary a.term, a.semantictag
		having count(a.id) > 1) as duplicate
	where a.term = duplicate.term;
	drop table if exists temp_active_fsn_hierarchy;
	drop table if exists tmp_description_syn;
	