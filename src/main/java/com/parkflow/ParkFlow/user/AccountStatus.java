package com.parkflow.ParkFlow.user;

/**
 * ACTIVE: can use everything for the role. Customers and admins start ACTIVE.
 * PENDING: a provider waiting for an admin. They can log in and add buildings, but the buildings stay hidden.
 * REJECTED: an admin turned the provider down. They can still log in; their buildings stay hidden.
 */
public enum AccountStatus { ACTIVE, PENDING, REJECTED }
