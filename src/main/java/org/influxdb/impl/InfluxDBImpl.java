package org.influxdb.impl;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.OkClient;
import retrofit.client.Response;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.squareup.okhttp.OkHttpClient;

/**
 * Implementation of a InluxDB API.
 * 
 * @author stefan.majer [at] gmail.com
 * 
 */
public class InfluxDBImpl implements InfluxDB {
	private final String username;
	private final String password;
	private final RestAdapter restAdapter;
	private final InfluxDBService influxDBService;

	/**
	 * Constructor which should only be used from the InfluxDBFactory.
	 * 
	 * @param url
	 *            the url where the influxdb is accessible.
	 * @param username
	 *            the user to connect.
	 * @param password
	 *            the password for this user.
	 */
	public InfluxDBImpl(final String url, final String username, final String password) {
		super();
		this.username = username;
		this.password = password;
		OkHttpClient okHttpClient = new OkHttpClient();
		this.restAdapter = new RestAdapter.Builder()
				.setEndpoint(url)
				.setErrorHandler(new InfluxDBErrorHandler())
				.setClient(new OkClient(okHttpClient))
				.build();

		this.influxDBService = this.restAdapter.create(InfluxDBService.class);
	}

	@Override
	public InfluxDB setLogLevel(final LogLevel logLevel) {
		switch (logLevel) {
		case NONE:
			this.restAdapter.setLogLevel(retrofit.RestAdapter.LogLevel.NONE);
			break;
		case BASIC:
			this.restAdapter.setLogLevel(retrofit.RestAdapter.LogLevel.BASIC);
			break;
		case HEADERS:
			this.restAdapter.setLogLevel(retrofit.RestAdapter.LogLevel.HEADERS);
			break;
		case FULL:
			this.restAdapter.setLogLevel(retrofit.RestAdapter.LogLevel.FULL);
			break;
		default:
			break;
		}
		return this;
	}

	@Override
	public Pong ping() {
		Stopwatch watch = Stopwatch.createStarted();
		Response response = this.influxDBService.ping();
		List<Header> headers = response.getHeaders();
		String version = "unknown";
		for (Header header : headers) {
			if (null != header.getName() && header.getName().equalsIgnoreCase("X-Influxdb-Version")) {
				version = header.getValue();
			}
		}
		Pong pong = new Pong();
		pong.setVersion(version);
		pong.setResponseTime(watch.elapsed(TimeUnit.MILLISECONDS));
		return pong;
	}

	@Override
	public String version() {
		return ping().getVersion();
	}

	@Override
	public void write(final String database, final String retentionPolicy, final Point point) {
		BatchPoints batchPoints = new BatchPoints.Builder(database).retentionPolicy(retentionPolicy).build();
		batchPoints.point(point);
		this.write(batchPoints);
	}

	@Override
	public void write(final BatchPoints batchPoints) {
		this.influxDBService.batchPoints(this.username, this.password, batchPoints);
	}

	@Override
	public void writeAsync(final BatchPoints batchPoints) {
		Callback<String> cb = new Callback<String>() {

			@Override
			public void success(final String t, final Response response) {
				// NOTHING TO do
			}

			@Override
			public void failure(final RetrofitError error) {
				new InfluxDBErrorHandler().handleError(error);
			}
		};
		this.influxDBService.batchPoints(this.username, this.password, batchPoints, cb);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public QueryResult query(final Query query) {
		QueryResult response = this.influxDBService.query(
				this.username,
				this.password,
				query.getDatabase(),
				query.getCommand());
		System.out.println(response);
		// FIXME
		return response;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void createDatabase(final String name) {
		Preconditions.checkArgument(!name.contains("-"), "Databasename cant contain -");
		this.influxDBService.query(this.username, this.password, "CREATE DATABASE " + name);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deleteDatabase(final String name) {
		this.influxDBService.query(this.username, this.password, "DROP DATABASE " + name);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> describeDatabases() {
		QueryResult result = this.influxDBService.query(this.username, this.password, "SHOW DATABASES");
		// {"results":[{"series":[{"name":"databases","columns":["name"],"values":[["mydb"]]}]}]}
		// Series [name=databases, columns=[name], values=[[mydb], [unittest_1433605300968]]]
		List<List<Object>> databaseNames = result.getResults().get(0).getSeries().get(0).getValues();
		List<String> databases = Lists.newArrayList();
		for (List<Object> database : databaseNames) {
			databases.add(database.get(0).toString());
		}
		return databases;
	}

}
