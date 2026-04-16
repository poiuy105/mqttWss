package io.emqx.mqtt

class Subscription(var topic: String, var qos: Int, var lastMessage: String = "")