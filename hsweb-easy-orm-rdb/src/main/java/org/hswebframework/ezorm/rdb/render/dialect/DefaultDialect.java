package org.hswebframework.ezorm.rdb.render.dialect;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.core.param.ClassFieldTerm;
import org.hswebframework.ezorm.rdb.render.dialect.function.SqlFunction;
import org.hswebframework.ezorm.rdb.render.dialect.term.BoostTermTypeMapper;
import org.hswebframework.ezorm.core.param.SqlTerm;
import org.hswebframework.ezorm.core.param.Term;
import org.hswebframework.ezorm.core.param.TermType;
import org.hswebframework.ezorm.rdb.executor.SqlExecutor;
import org.hswebframework.ezorm.rdb.meta.RDBColumnMetaData;
import org.hswebframework.ezorm.rdb.meta.parser.OracleTableMetaParser;
import org.hswebframework.ezorm.rdb.meta.parser.TableMetaParser;
import org.hswebframework.ezorm.rdb.render.SqlAppender;

import java.sql.JDBCType;
import java.util.*;

@Slf4j
public abstract class DefaultDialect implements Dialect {
    protected Map<String, TermTypeMapper> termTypeMappers = new HashMap<>();
    protected Map<String, DataTypeMapper> dataTypeMappers = new HashMap<>();
    protected Map<String, SqlFunction> functions = new HashMap<>();
    protected DataTypeMapper defaultDataTypeMapper = null;

    protected Map<String, JDBCType> jdbcTypeMap = new HashMap<>();

    public DefaultDialect() {
        //默认查询条件支持
        termTypeMappers.put(TermType.eq, BoostTermTypeMapper.notSupportArray((wherePrefix, term, column, tableAlias) ->
                new SqlAppender().add(
                        buildColumnName(tableAlias, column.getName()),
                        "=#{", wherePrefix,
                        (term instanceof ClassFieldTerm) ? "" : ".value}")));

        termTypeMappers.put(TermType.not, BoostTermTypeMapper.notSupportArray((wherePrefix, term, column, tableAlias) ->
                new SqlAppender().add(buildColumnName(tableAlias, column.getName()), "!=#{", wherePrefix, (term instanceof ClassFieldTerm) ? "" : ".value}")));

        termTypeMappers.put(TermType.like, BoostTermTypeMapper.notSupportArray((wherePrefix, term, column, tableAlias) -> {
            SqlAppender sqlAppender = new SqlAppender();
            boolean reverse = term.getOptions().contains("reverse");
            boolean startWith = term.getOptions().contains("startWith");
            boolean endWith = term.getOptions().contains("endWith");
            Dialect dialect = column.getTableMetaData().getDatabaseMetaData().getDialect();

            String columnName = buildColumnName(tableAlias, column.getName());

            if (reverse) {
                SqlFunction concat = dialect.getFunction("concat");
                if (concat != null) {
                    List<String> params = new ArrayList<>();
                    if (endWith) {
                        params.add("'%'");
                    }
                    params.add(columnName);
                    if (startWith) {
                        params.add("'%'");
                    }
                    columnName = startWith || endWith ? concat.apply(SqlFunction.Param.of(RenderPhase.where, params)) : columnName;
                }
                sqlAppender.add("#{", wherePrefix, (term instanceof ClassFieldTerm) ? "" : ".value}", " LIKE ", columnName);

            } else {
                sqlAppender.add(columnName, " LIKE #{", wherePrefix, (term instanceof ClassFieldTerm) ? "" : ".value}");
            }

            return sqlAppender;

        }));

        termTypeMappers.put(TermType.nlike, BoostTermTypeMapper.notSupportArray((wherePrefix, term, column, tableAlias) -> {
            SqlAppender sqlAppender = new SqlAppender();
            boolean reverse = term.getOptions().contains("reverse");
            String columnName = buildColumnName(tableAlias, column.getName());

            if (reverse) {
                boolean startWith = term.getOptions().contains("startWith");
                boolean endWith = term.getOptions().contains("endWith");
                Dialect dialect = column.getTableMetaData().getDatabaseMetaData().getDialect();

                SqlFunction concat = dialect.getFunction("concat");
                if (concat != null) {
                    List<String> params = new ArrayList<>();
                    if (endWith) {
                        params.add("'%'");
                    }
                    params.add(columnName);
                    if (startWith) {
                        params.add("'%'");
                    }
                    columnName = startWith || endWith ? concat.apply(SqlFunction.Param.of(RenderPhase.where, params)) : columnName;
                }
                sqlAppender.add("#{", wherePrefix, (term instanceof ClassFieldTerm) ? "" : ".value}", " NOT LIKE ", columnName);

            } else {
                sqlAppender.add(columnName, " NOT LIKE #{", wherePrefix, (term instanceof ClassFieldTerm) ? "" : ".value}");
            }

            return sqlAppender;

        }));
        termTypeMappers.put(TermType.isnull, (wherePrefix, term, column, tableAlias) ->
                new SqlAppender().add(buildColumnName(tableAlias, column.getName()), " IS NULL"));
        termTypeMappers.put(TermType.notnull, (wherePrefix, term, column, tableAlias) ->
                new SqlAppender().add(buildColumnName(tableAlias, column.getName()), " IS NOT NULL"));
        termTypeMappers.put(TermType.gt, BoostTermTypeMapper.notSupportArray((wherePrefix, term, column, tableAlias) ->
                new SqlAppender().add(buildColumnName(tableAlias, column.getName()), ">#{", wherePrefix, (term instanceof ClassFieldTerm) ? "" : ".value}")));
        termTypeMappers.put(TermType.lt, BoostTermTypeMapper.notSupportArray((wherePrefix, term, column, tableAlias) ->
                new SqlAppender().add(buildColumnName(tableAlias, column.getName()), "<#{", wherePrefix, (term instanceof ClassFieldTerm) ? "" : ".value}")));
        termTypeMappers.put(TermType.gte, BoostTermTypeMapper.notSupportArray((wherePrefix, term, column, tableAlias) ->
                new SqlAppender().add(buildColumnName(tableAlias, column.getName()), ">=#{", wherePrefix, (term instanceof ClassFieldTerm) ? "" : ".value}")));
        termTypeMappers.put(TermType.lte, BoostTermTypeMapper.notSupportArray((wherePrefix, term, column, tableAlias) ->
                new SqlAppender().add(buildColumnName(tableAlias, column.getName()), "<=#{", wherePrefix, (term instanceof ClassFieldTerm) ? "" : ".value}")));
        termTypeMappers.put(TermType.empty, BoostTermTypeMapper.notSupportArray((wherePrefix, term, column, tableAlias) ->
                new SqlAppender().add(buildColumnName(tableAlias, column.getName()), "=''")));
        termTypeMappers.put(TermType.nempty, BoostTermTypeMapper.notSupportArray((wherePrefix, term, column, tableAlias) ->
                new SqlAppender().add(buildColumnName(tableAlias, column.getName()), "!=''")));

        termTypeMappers.put(TermType.btw, BoostTermTypeMapper.supportArray((wherePrefix, term, column, tableAlias) -> {
            SqlAppender sqlAppender = new SqlAppender();
            List<Object> objects = param2list(term.getValue());
            if (objects.size() == 1)
                objects.add(objects.get(0));
            term.setValue(objects);
            sqlAppender.add(buildColumnName(tableAlias, column.getName()), " ").addSpc("BETWEEN")
                    .add("#{", wherePrefix, ".value[0]}")
                    .add(" AND ", "#{", wherePrefix, ".value[1]}");
            return sqlAppender;
        }));
        termTypeMappers.put(TermType.nbtw, BoostTermTypeMapper.supportArray((wherePrefix, term, column, tableAlias) ->
        {
            SqlAppender sqlAppender = new SqlAppender();
            List<Object> objects = param2list(term.getValue());
            if (objects.size() == 1)
                objects.add(objects.get(0));
            term.setValue(objects);
            sqlAppender.add(buildColumnName(tableAlias, column.getName()), " ").addSpc("NOT BETWEEN")
                    .add("#{", wherePrefix, ".value[0]}")
                    .add(" AND ", "#{", wherePrefix, ".value[1]}");
            return sqlAppender;
        }));
        termTypeMappers.put(TermType.in, BoostTermTypeMapper.supportArray((wherePrefix, term, column, tableAlias) -> {
            List<Object> values = param2list(term.getValue());
            term.setValue(values);
            SqlAppender appender = new SqlAppender();
            appender.add(tableAlias, ".").addSpc(column.getName()).add("IN(");
            for (int i = 0; i < values.size(); i++) {
                appender.add("#{", wherePrefix, ".value[", i, "]}", ",");
            }
            appender.removeLast();
            appender.add(")");
            return appender;
        }));
        termTypeMappers.put(TermType.nin, BoostTermTypeMapper.supportArray((wherePrefix, term, column, tableAlias) -> {
            List<Object> values = param2list(term.getValue());
            term.setValue(values);
            SqlAppender appender = new SqlAppender();
            appender.add(tableAlias, ".").addSpc(column.getName()).add("NOT IN(");
            for (int i = 0; i < values.size(); i++) {
                appender.add("#{", wherePrefix, ".value[", i, "]}", ",");
            }
            appender.removeLast();
            appender.add(")");
            return appender;
        }));
    }

    @Override
    public SqlFunction getFunction(String name) {
        return functions.get(name);
    }

    @Override
    public SqlFunction installFunction(String name, SqlFunction function) {
        return functions.put(name, function);
    }

    @Override
    public SqlAppender buildCondition(String wherePrefix, Term term, RDBColumnMetaData column, String tableAlias) {
        if (term instanceof SqlTerm) {
            SqlTerm sqlTerm = ((SqlTerm) term);
            String sql = sqlTerm.getSql();
            if (sql == null) sql = sqlTerm.getColumn();
            if (sql == null) return new SqlAppender();
            TermTypeMapper mapper = TermTypeMapper.sql(sqlTerm.getSql(), sqlTerm.getParam());
            return mapper.accept(wherePrefix, sqlTerm, column, tableAlias);
        }
        if (term.getValue() instanceof TermTypeMapper) {
            return ((TermTypeMapper) term.getValue()).accept(wherePrefix, term, column, tableAlias);
        }
        TermTypeMapper mapper = termTypeMappers.get(term.getTermType());
        if (mapper == null) mapper = termTypeMappers.get(TermType.eq);
        return mapper.accept(wherePrefix, term, column, tableAlias);
    }

    @SuppressWarnings("unchecked")
    protected List<Object> param2list(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List) return (List) value;
        if (value instanceof Collection) return new ArrayList<>(((Collection) value));
        if (value.getClass().isArray()) {
            return new ArrayList<>(Arrays.asList(((Object[]) value)));
        } else {
            return new ArrayList<>(Collections.singletonList(value));
        }
    }

    @Override
    public void setTermTypeMapper(String termType, TermTypeMapper mapper) {
        termTypeMappers.put(termType.toLowerCase(), mapper);
    }

    @Override
    public boolean isSupportTermType(String termType) {
        return termTypeMappers.containsKey(termType);
    }

    @Override
    public void setDataTypeMapper(JDBCType jdbcType, DataTypeMapper mapper) {
        dataTypeMappers.put(jdbcType.getName(), mapper);
    }

    public void setJdbcTypeMapping(String dataType, JDBCType jdbcType) {
        jdbcTypeMap.put(dataType, jdbcType);
    }

    @Override
    public String buildDataType(RDBColumnMetaData columnMetaData) {
        if (columnMetaData.getJdbcType() == null) return null;
        DataTypeMapper mapper = dataTypeMappers.get(columnMetaData.getJdbcType().getName());
        if (null == mapper) mapper = defaultDataTypeMapper;
        return mapper.getDataType(columnMetaData);
    }

    @Override
    public JDBCType getJdbcType(String dataType) {
        JDBCType jdbcType;
        try {
            jdbcType = JDBCType.valueOf(dataType.toUpperCase());
        } catch (Exception e) {
            if (dataType.contains("("))
                dataType = dataType.substring(0, dataType.indexOf("("));
            jdbcType = jdbcTypeMap.get(dataType.toLowerCase());
            if (jdbcType == null) {
                //出现此警告可以通过 setJdbcTypeMapping注册一些奇怪的类型
                log.warn("can not parse jdbcType:{}", dataType);
                jdbcType = JDBCType.OTHER;
            }
        }
        return jdbcType;
    }

    @Setter
    @Getter
    private boolean preparePagingSql = Boolean.getBoolean("easyorm.paging.prepare");

    @Override
    public String doPaging(String sql, int pageIndex, int pageSize) {
        return doPaging(sql, pageIndex, pageSize, preparePagingSql);
    }
}
