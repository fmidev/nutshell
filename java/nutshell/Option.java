package nutshell;

import java.util.Map;

public interface Option {

    String getName();

    String getDescription();

    void setParams(String params) throws NoSuchFieldException, IllegalAccessException;

    void setParams(String params[]) throws NoSuchFieldException, IllegalAccessException;

    void setParam(String key, Object value) throws NoSuchFieldException, IllegalAccessException;

    boolean hasParams();

    Map<String, Object> getParams();

    String[] getParamKeys();

    Object[] getValues();
}
