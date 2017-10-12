package src;

/** Represents the 7 types of commands used by the agent and server to communicate. */
enum Command {
  REGISTER, REGISTERED, FETCH, FETCHRESPONSE, UNREGISTER, PROBE, ACK
}
