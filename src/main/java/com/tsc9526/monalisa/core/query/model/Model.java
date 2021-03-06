/*******************************************************************************************
 *	Copyright (c) 2016, zzg.zhou(11039850@qq.com)
 * 
 *  Monalisa is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU Lesser General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.

 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU Lesser General Public License for more details.

 *	You should have received a copy of the GNU Lesser General Public License
 *	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************************/
package com.tsc9526.monalisa.core.query.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.tsc9526.monalisa.core.annotation.Column;
import com.tsc9526.monalisa.core.annotation.Table;
import com.tsc9526.monalisa.core.datasource.DBConfig;
import com.tsc9526.monalisa.core.datasource.DataSourceManager;
import com.tsc9526.monalisa.core.datasource.DbProp;
import com.tsc9526.monalisa.core.generator.DBMetadata;
import com.tsc9526.monalisa.core.logger.Logger;
import com.tsc9526.monalisa.core.meta.MetaPartition;
import com.tsc9526.monalisa.core.meta.MetaTable;
import com.tsc9526.monalisa.core.meta.MetaTable.CreateTable;
import com.tsc9526.monalisa.core.meta.MetaTable.TableType;
import com.tsc9526.monalisa.core.query.Query;
import com.tsc9526.monalisa.core.query.Tx;
import com.tsc9526.monalisa.core.query.Tx.Executable;
import com.tsc9526.monalisa.core.query.TxQuery;
import com.tsc9526.monalisa.core.query.dao.Delete;
import com.tsc9526.monalisa.core.query.dao.Insert;
import com.tsc9526.monalisa.core.query.dao.Update;
import com.tsc9526.monalisa.core.query.dialect.Dialect;
import com.tsc9526.monalisa.core.query.partition.Partition;
import com.tsc9526.monalisa.core.tools.ClassHelper.FGS;
import com.tsc9526.monalisa.core.tools.JavaBeansHelper;
import com.tsc9526.monalisa.core.tools.ModelHelper;
import com.tsc9526.monalisa.core.tools.TypeHelper;



/**
 * 数据库表模型
 * 
 * 
 * @author zzg.zhou(11039850@qq.com)
 * 
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public abstract class Model<T extends Model> implements Serializable {
	static Logger logger=Logger.getLogger(Model.class);

	private static final long serialVersionUID = 703976566431364670L;

	protected static DataSourceManager dsm = DataSourceManager.getInstance();
	private static Map<String, Table>  hCachePartitionTables = new ConcurrentHashMap<String, Table>();
	private static Map<String, String> hCacheHistoryTables   = new ConcurrentHashMap<String, String>();

	protected transient ModelMeta modelMeta;
	protected transient ModelHolder modelHolder;
	protected transient DBConfig db;

	protected String TABLE_NAME;
	protected String[] PRIMARY_KEYS;

	public Model() {
	}

	public Model(String tableName, String... primaryKeys) {
		this.TABLE_NAME = tableName;
		this.PRIMARY_KEYS = primaryKeys;
	}

	protected ModelMeta mm() {
		if (modelMeta == null) {
			modelMeta = ModelMeta.getModelMeta(this);
		}
		return modelMeta;
	}

	protected synchronized ModelHolder holder() {
		if (modelHolder == null) {
			modelHolder = new ModelHolder(this);
		}
		return modelHolder;
	}

	/**
	 * 设置访问数据库
	 * 
	 * @param db database
	 * @return this 
	 */
	public T use(DBConfig db) {
		this.db = db;

		return (T) this;
	}
	
	/**
	 * @return  The Used DBConfig
	 */
	public DBConfig use(){
		return this.db;
	}
	

	/**
	 * Check if the model is dirty
	 * 
	 * @return true if dirty otherwise false
	 */
	public boolean dirty() {
		return holder().dirty;
	}

	/**
	 * Set the model dirty
	 * 
	 * @param dirty dirty
	 * @return this
	 */
	public T dirty(boolean dirty) {
		holder().dirty = dirty;
		return (T) this;
	}

	/**
	 * Check if the model is db entity
	 * 
	 * @return true if is db entity otherwise false
	 */
	public boolean entity() {
		return holder().entity;
	}

	/**
	 * Set the model db entity
	 * 
	 * @param entity entity
	 * @return this
	 */
	public T entity(boolean entity) {
		holder().entity = entity;
		return (T) this;
	}

	/**
	 * @param dataObject
	 *            HttpServletRequest, Map, JsonObject, String(json/xml),
	 *            JavaBean
	 * @param mappings
	 *            [Options] Translate dataObject field to model field <br>
	 *            For example: <br>
	 *            "user_id=id", ... // Parse dataObject.user_id to Model.id<br>
	 *            Another example:<br>
	 *            "~XXX." //Only parse dataObject's fields with prefix: "XXX."
	 *            
	 * @return this
	 * 
	 * @see com.tsc9526.monalisa.core.tools.ModelHelper#parse(Model,Object,String ...)
	 */
	public T parse(Object dataObject, String... mappings) {
		ModelHelper.parse(this, dataObject, mappings);

		return (T) this;
	}

	/**
	 * Load by primary keys
	 * 
	 * @return this;
	 */
	public T load() {
		Query query = dialect().load(this);

		query.use(db());

		Object r = query.load(this);
		return (T) r;
	}

	/**
	 * 存储对象到数据库
	 * 
	 * @return 成功变更的记录数
	 */
	public int save() {
		if (history()) {
			return Tx.execute(new Executable() {
				public int execute() {
					int r=doSave();
					saveHistory(ModelEvent.INSERT);
					return r;
				}
			});
		} else {
			return doSave();
		}
	}

	protected int doSave() {
		int r = -1;
		before(ModelEvent.INSERT);
		doValidate();
		r = new Insert(this).insert(false);
		after(ModelEvent.INSERT, r);
		return r;
	}

	/**
	 * 存储对象到数据库， 如果主键冲突， 则执行更新操作
	 * 
	 * @return 成功变更的记录数
	 */
	public int saveOrUpdate() {
		if (history()) {
			return Tx.execute(new Executable() {
				public int execute() {
					int r=doSaveOrUpdate();
					saveHistory(ModelEvent.REPLACE);
					return r;
				}
			});
		} else {
			return doSaveOrUpdate();
		}
	}

	protected int doSaveOrUpdate() {
		int r = -1;
		before(ModelEvent.REPLACE);
		doValidate();
		r = new Insert(this).insert(true);
		after(ModelEvent.REPLACE, r);
		return r;
	}

	/**
	 * 更新对象到数据库
	 * 
	 * @return 成功变更的记录数
	 */
	public int update() {
		if (history()) {
			return Tx.execute(new Executable() {
				public int execute() {
					int r= doUpdate();
					saveHistory(ModelEvent.UPDATE);
					return r;
				}
			});
		} else {
			return doUpdate();
		}
	}

	protected int doUpdate() {
		int r = -1;
		before(ModelEvent.UPDATE);
		doValidate();
		r = new Update(this).update();
		after(ModelEvent.UPDATE, r);
		return r;
	}

	/**
	 * 从数据库删除该记录
	 * 
	 * @return 成功变更的记录数
	 */
	public int delete() {
		if (history()) {
			return Tx.execute(new Executable() {
				public int execute() {
					int r= doDelete();
					saveHistory(ModelEvent.DELETE);
					return r;
				}
			});
		} else {
			return doDelete();
		}

	}

	protected int doDelete() {
		int r = -1;
		before(ModelEvent.DELETE);
		r = new Delete(this).delete();
		after(ModelEvent.DELETE, r);
		return r;
	}

	protected void saveHistory(ModelEvent event) {
		List<FGS> pks=mm().getPkFields();
		if(pks.size()<1){
			return;
		}
		
		DBConfig db = mm().db;
		String table = mm().tableName;
		
		DBConfig historyDB = db;
		String hdb = DbProp.PROP_DB_HISTORY_DB.getValue(db);
		if (hdb != null && hdb.length() > 0) {
			historyDB = dsm.getDBConfig(hdb, null);
			if(historyDB==null){
				historyDB = db.getByConfigName(hdb);
			}							
		}
		if (historyDB == null) {
			throw new RuntimeException("History db config: " + hdb + " not found! Current db: " + db.getKey() + ", table: " + table);
		}

		String historyTableName = DbProp.PROP_DB_HISTORY_PREFIX_TABLE.getValue(db) + table;
		String key=historyDB.getKey()+":"+historyTableName;
		if(!hCacheHistoryTables.containsKey(key)){
			CreateTable createTable = dialect().getCreateTable(db, table);
			CreateTable historyTable = createTable.createTable(TableType.HISTORY, historyTableName);
			dialect().createTable(historyDB, historyTable);			
			hCacheHistoryTables.put(key, key);
		}
		
		TxQuery q = Tx.getTxQuery();
		String prefix = DbProp.PROP_DB_HISTORY_PREFIX_COLUMN.getValue(db);
		Record history = new Record(historyTableName);
		history.use(historyDB);
		
		
		Model m=MMH.createFrom(this);
		for(FGS fgs:pks){			 
			Column c=fgs.getAnnotation(Column.class);
			if(c.key()){
				m.set(c.name(), fgs.getObject(this)); 
			}
		}
		m.load();				
		history.parse(m);
		
		for(FGS fgs:changedFields()){
			String name =fgs.getFieldName();
			FGS x=history.field(name);			 			 			 
			if(x!=null){
				x.setObject(history, fgs.getObject(this));
			}
		}
		
		history.set(prefix + "time", new Date());
		history.set(prefix + "type", event.name());
		history.set(prefix + "txid", q == null ? "" : q.getTxid());
		history.set(prefix + "user", Tx.getContext(Tx.CONTEXT_CURRENT_USERID));
		
		history.save();
	}

	protected boolean history() {
		String h = DbProp.PROP_DB_HISTORY_TABLES.getValue(mm().db);
		if (h != null && h.trim().length() > 0) {
			String prefix = DbProp.PROP_DB_HISTORY_PREFIX_TABLE.getValue(mm().db);			
			String n = mm().tableName;
			if(n.startsWith(prefix)){
				return false;
			}
			
			String[] xs = h.trim().split(",|;|\\|");
			for (String x : xs) {
				x = x.trim().replace("%", ".*");

				if (n.matches(x)) {
					return true;
				}
			}
		}
		return false;
	}

	public void before(ModelEvent event) {
		if (event == ModelEvent.INSERT || event == ModelEvent.UPDATE || event==ModelEvent.REPLACE) {
			Date now = new Date();

			String createTimeColumn = DbProp.PROP_TABLE_AUTO_SET_CREATE_TIME.getValue(mm().db, mm().tableName);
			String updateTimeColumn = DbProp.PROP_TABLE_AUTO_SET_UPDATE_TIME.getValue(mm().db, mm().tableName);

			String createByColumn = DbProp.PROP_TABLE_AUTO_SET_CREATE_BY.getValue(mm().db, mm().tableName);
			String updateByColumn = DbProp.PROP_TABLE_AUTO_SET_UPDATE_BY.getValue(mm().db, mm().tableName);
			
			if (event == ModelEvent.INSERT || event==ModelEvent.REPLACE){ 
				if(createTimeColumn != null && createTimeColumn.trim().length() > 0) {
					FGS createTime = field(createTimeColumn.trim());
					if (createTime != null && createTime.getObject(this) == null) {
						createTime.setObject(this, now);
					}
				}
				
				if(createByColumn != null && createByColumn.trim().length() > 0) {
					FGS createBy = field(createByColumn.trim());					
					if (createBy != null && createBy.getObject(this) == null) {
						Object user=Tx.getContext(Tx.CONTEXT_CURRENT_USERID);
						if(user!=null){
							createBy.setObject(this, user);
						}
					}
				}
			}

			if (event == ModelEvent.UPDATE || event==ModelEvent.REPLACE){
				if(updateTimeColumn != null && updateTimeColumn.trim().length() > 0) {			
					FGS updateTime = field(updateTimeColumn.trim());
					if (updateTime != null && updateTime.getObject(this) == null) {
						updateTime.setObject(this, now);
					}
				}
				
				if(updateByColumn != null && updateByColumn.trim().length() > 0) {
					FGS updateBy = field(updateByColumn.trim());					
					if (updateBy != null && updateBy.getObject(this) == null) {
						Object user=Tx.getContext(Tx.CONTEXT_CURRENT_USERID);
						if(user!=null){
							updateBy.setObject(this, user);
						}
					}
				}
			}
		}

		if (mm().listener != null) {
			mm().listener.before(event, this);
		}
	}

	public void after(ModelEvent event, int r) {
		if (r >= 0) {
			dirty(false);

			switch (event) {
			case INSERT:
				entity(true);
				break;
			case DELETE:
				entity(false);
				break;
			case UPDATE:
				entity(true);
				break;
			case REPLACE:
				entity(true);
				break;
			case LOAD:
				entity(true);
				break;
			}
		}

		if (mm().listener != null) {
			mm().listener.after(event, this, r);
		}
	}

	/**
	 * 设置所有字段为null, 并清除改变字段集合
	 * 
	 * @return this
	 */
	public T clear() {
		for (FGS fgs : fields()) {
			fgs.setObject(this, null);
		}

		holder().clearChanges();

		entity(false);

		return (T) this;
	}

	/**
	 * 设置所有字段为缺省值
	 * 
	 * @return this
	 */
	public T defaults() {
		for (FGS fgs : fields()) {
			Column c = fgs.getAnnotation(Column.class);
			if(!c.auto()){
				String v = c.value();
				if ("NULL".equalsIgnoreCase(v)) {
					if (c.notnull()) {
						Object x = JavaBeansHelper.getDefaultValue(c.jdbcType(), fgs.getType());
						fgs.setObject(this, x);
					}
				} else {
					if(TypeHelper.isNumber(c.jdbcType())){
						fgs.setObject(this, Double.parseDouble(v));
					}else{
						fgs.setObject(this, v);
					}
				}
			}
		}

		entity(false);

		return (T) this;
	}

	/**
	 * 
	 * @return 返回数据库方言
	 */
	public Dialect dialect() {
		return mm().dialect;
	}

	/**
	 * 
	 * @return 返回表的字段列表
	 */
	public Collection<FGS> fields() {
		return mm().fields();
	}

	/**
	 * 
	 * @return fields if called set("xxx",value) or setXxx()
	 */
	public Collection<FGS> changedFields() {
		return holder().changedFields();
	}

	protected void fieldChanged(String fieldJavaName) {
		holder().fieldChanged(fieldJavaName);
	}

	public FGS field(String name) {
		return mm().findFieldByName(name);
	}

	public boolean updateKey() {
		return holder().updateKey;
	}

	/**
	 * How to update the model's primary key:<br><br>
	 * <code>
	 *   Update update=new Update(model); <br>
	 *   model.updateKey(true); //Set enable update primary key! <br>
	 *   model.setId(newId);    //Set new primary key <br>
	 *   model.setXxx ...       //Set other fields ...<br>
	 *   update.update("id=?",oldId);<br>
	 * </code>
	 * 
	 * @param updateKey updateKey
	 * 
	 * @return Enable update the model's primary key, default is: false
	 */
	public T updateKey(boolean updateKey) {
		return (T) this;
	}

	public T set(String name, Object value) {
		FGS fgs = field(name);
		if (fgs != null) {
			fgs.setObject(this, value);

			if (fgs.getSetMethod() == null) {
				holder().fieldChanged(fgs.getFieldName());
			}
		} else {
			String throwException = DbProp.PROP_TABLE_EXCEPTION_IF_SET_FIELD_NOT_FOUND.getValue(mm().db, mm().tableName);
			if ("true".equalsIgnoreCase(throwException)) {
				throw new RuntimeException("Field not found: " + name);
			}
		}

		return (T) this;
	}

	public <P> P  get(String name){
		FGS fgs = field(name);
		if (fgs != null) {
			return (P)fgs.getObject(this);
		}
		return null;
	}
	
	public <P> P get(String name,P defaultValue) {
		P p=get(name);
		if(p==null){
			p=defaultValue;
		}
		return p;
	}

	/**
	 * 
	 * @return 返回表名等信息
	 */
	public Table table() {
		Table table = mm().table;
		if (mm().mp != null) {
			table = createTable(mm().mp);
		}

		return table;
	}

	protected Table createTable(MetaPartition mp) {
		DBConfig db = db();
		Partition<Model<?>> partition = mp.getPartition();
		String tableName = partition.getTableName(mp, this);
		String tableKey = db.getKey() + ":" + tableName;

		Table table = hCachePartitionTables.get(tableKey);
		if (table == null) {
			String tablePrefix = mp.getTablePrefix();
			MetaTable metaTable = DBMetadata.getMetaTable(db.getKey(), tablePrefix);
			if (metaTable == null || metaTable.getCreateTable() == null) {
				throw new RuntimeException("Fail create table: " + tableName + ", db: " + db.getKey() + ", MetaTable not found: " + tablePrefix);
			}

			try {
				CreateTable createTable = metaTable.getCreateTable().createTable(TableType.PARTITION, tableName);
				db.getDialect().createTable(db, createTable);
				table = ModelMeta.createTable(tableName, mm().table);
				hCachePartitionTables.put(tableKey, table);
			} catch (Exception e) {
				throw new RuntimeException("Fail create table: " + tableName + ", db: " + db.getKey(), e);
			}
		}
		return table;
	}

	/**
	 * 
	 * @return 返回表的自增字段， 如果没有则返回null
	 */
	public FGS autoField() {
		return mm().autoField;
	}

	/**
	 * 
	 * @return 数据库连接信息
	 */
	public DBConfig db() {
		return db == null ? mm().db : this.db;
	}

	public boolean readonly() {
		return holder().readonly;
	}

	public void readonly(boolean readonly) {
		holder().readonly = readonly;
	}

	/**
	 * 
	 * @return 逗号分隔的字段名， *： 表示返回所有字段
	 */
	public String filterFields() {
		return holder().filterFields();
	}

	/**
	 * 排除表的某些字段。 用于在查询表时， 过滤掉某些不必要的字段
	 * 
	 * @param fields    要排除的字段名
	 * 
	 * @return 模型本身
	 */
	public T exclude(String... fields) {
		holder().exclude(fields);

		return (T) this;
	}

	/**
	 * 排除大字段（字段长度 大于等于 #Short.MAX_VALUE)
	 * 
	 * @return 模型本身
	 */
	public T excludeBlobs() {
		holder().excludeBlobs();

		return (T) this;
	}

	/**
	 * 排除超过指定长度的字段
	 * 
	 * @param maxLength   字段长度
	 * 
	 * @return 模型本身
	 */
	public T excludeBlobs(int maxLength) {
		holder().excludeBlobs(maxLength);

		return (T) this;
	}

	/**
	 * 只提取某些字段
	 * 
	 * @param fields  需要的字段名称
	 * @return 模型本身
	 */
	public T include(String... fields) {
		holder().include(fields);

		return (T) this;
	}

	/**
	 * 复制对象数据
	 * 
	 * @return copy of this model
	 */
	public T copy() {
		return (T) mm().copyModel(this);
	}

	protected void doValidate() {
		mm().doValidate(this);
	}

	/**
	 * 校验字段数据的是否合法.
	 * 
	 * @return 不合法的字段列表{字段名: 错误信息}. 如果没有错误, 则为空列表.
	 */
	public List<String> validate() {
		return mm().validate(this);
	}

	public List<ModelIndex> indexes() {
		return mm().indexes;
	}

	public List<ModelIndex> uniqueIndexes() {
		return mm().uniqueIndexes();
	}

	public ModelListener listener() {
		return mm().listener;
	}

	public String toString() {
		return ModelHelper.toString(this);
	}

	public String toJson() {
		return ModelHelper.toJson(this);
	}

	public String toXml() {
		return toXml(true, true);
	}

	public String toXml(boolean withXmlHeader, boolean ignoreNullFields) {
		return ModelHelper.toXml(this, withXmlHeader, ignoreNullFields);
	}
}
