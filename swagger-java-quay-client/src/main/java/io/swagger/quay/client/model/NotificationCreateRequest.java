package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Information for creating a notification on a repository
 **/
@ApiModel(description = "Information for creating a notification on a repository")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class NotificationCreateRequest   {
  
  private Object eventConfig = null;
  private String title = null;
  private Object config = null;
  private String event = null;
  private String method = null;

  
  /**
   * JSON config information for the specific event of notification
   **/
  @ApiModelProperty(required = true, value = "JSON config information for the specific event of notification")
  @JsonProperty("eventConfig")
  public Object getEventConfig() {
    return eventConfig;
  }
  public void setEventConfig(Object eventConfig) {
    this.eventConfig = eventConfig;
  }

  
  /**
   * The human-readable title of the notification
   **/
  @ApiModelProperty(value = "The human-readable title of the notification")
  @JsonProperty("title")
  public String getTitle() {
    return title;
  }
  public void setTitle(String title) {
    this.title = title;
  }

  
  /**
   * JSON config information for the specific method of notification
   **/
  @ApiModelProperty(required = true, value = "JSON config information for the specific method of notification")
  @JsonProperty("config")
  public Object getConfig() {
    return config;
  }
  public void setConfig(Object config) {
    this.config = config;
  }

  
  /**
   * The event on which the notification will respond
   **/
  @ApiModelProperty(required = true, value = "The event on which the notification will respond")
  @JsonProperty("event")
  public String getEvent() {
    return event;
  }
  public void setEvent(String event) {
    this.event = event;
  }

  
  /**
   * The method of notification (such as email or web callback)
   **/
  @ApiModelProperty(required = true, value = "The method of notification (such as email or web callback)")
  @JsonProperty("method")
  public String getMethod() {
    return method;
  }
  public void setMethod(String method) {
    this.method = method;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class NotificationCreateRequest {\n");
    
    sb.append("    eventConfig: ").append(StringUtil.toIndentedString(eventConfig)).append("\n");
    sb.append("    title: ").append(StringUtil.toIndentedString(title)).append("\n");
    sb.append("    config: ").append(StringUtil.toIndentedString(config)).append("\n");
    sb.append("    event: ").append(StringUtil.toIndentedString(event)).append("\n");
    sb.append("    method: ").append(StringUtil.toIndentedString(method)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
