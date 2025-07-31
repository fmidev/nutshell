package nutshell10;

import java.util.Map;

public class BeanLike implements Beanish {

    protected final String name;
    protected final String description;
    protected String[] paramKeys = new String[0];

    BeanLike(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // @Override
    public String getName() {
        return name;
    }

    // @Override
    public String getDescription() {
        return description;
    }

    public boolean hasParams() {
        return paramKeys.length > 0;
    }


    public void setParams(String args) throws NoSuchFieldException, IllegalAccessException {
        setParams(args.split(","));
    }

    public void setParam(String key, Object value) throws NoSuchFieldException, IllegalAccessException {
    }

    public void setParams(String[] args) throws NoSuchFieldException, IllegalAccessException {
    }

    public Map<String, Object> getParams() {
        return null;
    }

    public String[] getParamKeys() {
        return paramKeys;
    }

    public Object[] getValues() {
        return null;
    }

}