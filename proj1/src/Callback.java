package src;

// Used by threads to call back to the client when they successfully
// complete one iteration of their task.
interface TaskListener {
	// Called by the thread when the thread successfully completes a task.
	void onSuccess(Service service);
	// Called by the thread when it fails to complete a task.
	void onFailure(Service service);
}
