package main.ksets.kernel;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class KIII implements Serializable {

	private static final long serialVersionUID = -1945033929994699028L;
	private K2Layer[] k3;
	private int inputSize;
	private double[] emptyArray; // Empty array used during the resting period
	
	private transient ThreadPoolExecutor pool;
	
	public void save(String filename) throws IOException {
		FileOutputStream fos = new FileOutputStream(filename);
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(this);
		oos.close();
	}
	
	public static KIII load(String filename) throws IOException, ClassNotFoundException {
		FileInputStream fis = new FileInputStream(filename);
		ObjectInputStream ois = new ObjectInputStream(fis);
		KIII k3 = (KIII) ois.readObject();
		ois.close();
		return k3;
	}
	
	private void readObject(ObjectInputStream is) {
		try {
			is.defaultReadObject();
			pool = new ThreadPoolExecutor(4, 10, 10, TimeUnit.NANOSECONDS, new PriorityBlockingQueue<Runnable>());	
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Create a new KIII with the default configurations from the old matlab implementation.
	 * @param size
	 */
	public KIII(int size) {
		this.inputSize = size;
		this.emptyArray = new double[inputSize];
		
		k3 = new K2Layer[3];
		
		k3[0] = new K2Layer(size, Config.defaultW1, Config.defaultWLat1);
		k3[1] = new K2Layer(size, Config.defaultW2, Config.defaultWLat2);
		k3[2] = new K2Layer(size, Config.defaultW3, Config.defaultWLat3);
		
		k3[1].connect(k3[0], 0.3, -1);
		k3[2].connect(k3[0], 0.5, -1);
		
		k3[0].connect(k3[1], 0.5, -17);
		k3[0].connectInhibitory(k3[1], 0.6, -25);
		k3[2].connect(k3[1], 1, 1);
		
		k3[0].connectInhibitory(new LowerOutputAdapter(k3[2]), -0.5, 25);
		k3[1].connectInhibitory(k3[2], 0.5, 25);
		
		pool = new ThreadPoolExecutor(4, 10, 10, TimeUnit.NANOSECONDS, new PriorityBlockingQueue<Runnable>());	
	}
	
	/**
	 *  Initialize KIII-set with a perturbed input to move it from zero steady state and let it stabilize
	 */
	public double[][] initialize() {
		double[] perturbed = new double[inputSize];
		
		for (int i = 0; i < inputSize; ++i) {
			perturbed[i] = Math.random() - 0.5;
		}
		System.out.println(Arrays.toString(perturbed));
		this.step(perturbed, 1);
		this.step(emptyArray, 299);
		double[][] outputs = new double[1][];	
		outputs[0] = k3[0].getActivationDeviation();
		return outputs;
	}
	
	public void setExternalStimulus(double[] stimulus) {
		k3[0].setExternalStimulus(stimulus);
	}
	
	public double[] getFullOutput() {
		return this.getFullOutput(0);
	}
	
	public double[] getFullOutput(int delay) {
		return k3[2].getLayerOutput(0);
	}
	
	public void solve() {
		k3[0].solve();
		k3[1].solve();
		k3[2].solve();
	}
	
	public void solveAsync() {
		pool.execute(k3[0]);
		pool.execute(k3[1]);
		pool.execute(k3[2]);
		
		while(pool.getActiveCount() > 0){}
	}

	public void step(double[] stimulus, int times) {
		for (int i = 0; i < times; ++i) {
			solve();
			Config.incTime();
		}
	}

	public void stepAsync(double[] stimulus, int times) {
		for (int i = 0; i < times; ++i) {
			solveAsync();
			Config.incTime();
		}
	}

	public void train(ArrayList<double[]> data) {
		for (int i = 0; i < data.size(); ++i) {
			double[] stimulus = Arrays.copyOf(data.get(i), data.get(i).length);
			this.step(stimulus, Config.active);
			k3[2].train();
			this.step(emptyArray, Config.rest);
		}
	}
	
	public void trainAsync(ArrayList<double[]> data) {
		for (int i = 0; i < data.size(); ++i) {
			double[] stimulus = Arrays.copyOf(data.get(i), data.get(i).length);
			this.stepAsync(stimulus, Config.active);
			k3[2].train();
			this.stepAsync(emptyArray, Config.rest);
		}
	}

	public double[][] run(ArrayList<double[]> data) {
		double[][] outputs = new double[data.size()][];		
		for (int i = 0; i < data.size(); ++i) {
			double[] stimulus = Arrays.copyOf(data.get(i), data.get(i).length);
			// Stimulate the network (equivalent to an sniff)
			this.step(stimulus, Config.active);
			// Calculate the output as the standard deviation of the activation history of each top KII node
			outputs[i] = k3[0].getActivationDeviation();
			// Put the network to rest, to prepare it for the next stimulus
			this.step(emptyArray, Config.rest);
		}
		
		return outputs;
	}
	
	public void runAsync(ArrayList<double[]> data) {
		double[][] outputs = new double[data.size()][];
		for (int i = 0; i < data.size(); ++i) {
			double[] stimulus = Arrays.copyOf(data.get(i), data.get(i).length);			
			// Stimulate the network (equivalent to an sniff)
			this.stepAsync(stimulus, Config.active);
			// Calculate the output as the standard deviation of the activation history of each top KII node
			outputs[i] = k3[2].getActivationDeviation();			
			// Put the network to rest, to prepare it for the next stimulus
			this.stepAsync(emptyArray, Config.rest);
		}
	}
}
