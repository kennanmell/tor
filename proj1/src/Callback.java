package src;

// callback methods
abstract class Callback {
	void onSuccess(Service service){}
	void onSuccess(){}
	void onFailure(Service service){}
	void onFailure(){}
}