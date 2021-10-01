<?xml version="1.0" encoding="ISO-8859-1"?>
<web-app xmlns="http://java.sun.com/xml/ns/javaee"
	 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	 xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd"
	 version="2.5" > 

  <!-- NutShell conf $USER $HOSTNAME /-->

  <description>NutShell product demo - experimental </description>
  <display-name>NutShell Servlet</display-name>

  <servlet>
    <servlet-name>NutShell</servlet-name>
    <servlet-class>nutshell.Nutlet</servlet-class>
    <init-param>
      <param-name>confDir</param-name>
      <param-value>${NUTSHELL_CONF_DIR}</param-value>
    </init-param>
    <init-param>
      <param-name>buildDate</param-name>
      <param-value>${DATE}</param-value>
    </init-param>
    <init-param>
      <param-name>buildBy</param-name>
      <param-value>${USER}@${HOSTNAME}</param-value>
    </init-param>
  </servlet>

  <servlet-mapping>
    <servlet-name>NutShell</servlet-name>
    <url-pattern>/NutShell</url-pattern>
  </servlet-mapping>

  <error-page>
    <error-code>404</error-code>
    <location>/NutShell?product=resolve</location>
  </error-page>

  <!-- allow directory listings /-->
  <servlet>
    <servlet-name>listing</servlet-name>
    <servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class>
    <init-param>
      <param-name>debug</param-name>
      <param-value>0</param-value>
    </init-param>
    <init-param>
      <param-name>listings</param-name>
      <param-value>true</param-value>
    </init-param>
    <load-on-startup>1</load-on-startup>
  </servlet>

  <servlet-mapping>
    <servlet-name>listing</servlet-name>
    <!-- TO DO: selective /-->
    <url-pattern>/</url-pattern>
  </servlet-mapping>

  <!-- NutShell conf END /-->

</web-app>
