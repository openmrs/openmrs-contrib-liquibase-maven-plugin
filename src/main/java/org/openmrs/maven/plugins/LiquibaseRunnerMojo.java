/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.maven.plugins;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.liquibase.maven.plugins.MavenUtils;

import ch.vorburger.exec.ManagedProcessException;

/**
 * Goal which runs a liquibase changeset file.
 *
 * @goal run
 * 
 * @phase generate-resources
 */
public class LiquibaseRunnerMojo extends AbstractMojo {

	/**
	 * The fully qualified name of the driver class to use to connect to the
	 * database.
	 *
	 * @parameter property="liquibase.driver"
	 */
	private String driver;

	/**
	 * The Database URL to connect to for executing Liquibase.
	 *
	 * @parameter property="liquibase.url"
	 */
	private String url;

	/**
	 * The database username to use to connect to the specified database.
	 *
	 * @parameter property="liquibase.username"
	 */
	private String username;
	
	/**
	 * The database password to use to connect to the specified database.
	 *
	 * @parameter property="liquibase.password"
	 */
	private String password;
	
	/**
	 * The base directory.
	 *
	 * @parameter property="liquibase.baseDir"
	 */
	private String baseDir;
	
	/**
	 * The database data directory.
	 *
	 * @parameter property="liquibase.dataDir"
	 */
	private String dataDir;
	
	/**
	 * The liquibase change log file.
	 *
	 * @parameter property="liquibase.changeLogFile"
	 */
	private String changeLogFile;

	/**
	* The Maven project that plugin is running under.
	*
	* @parameter property="project"
	* @required
	*/
	private MavenProject project;

	public void execute() throws MojoExecutionException {
		try {
			DatabaseManager.getInstance().start(getPort(url), baseDir, dataDir);
			
			runLiquibaseFile(driver, url, username, password, changeLogFile);
		
			DatabaseManager.getInstance().stop();
		} 
		catch (ManagedProcessException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	private void runLiquibaseFile(String driver, String url, String name,
			String pw, String changeLogFile) {
		Database database = null;
		try {
			database = DatabaseFactory.getInstance()
					.findCorrectDatabaseImplementation(
							new JdbcConnection(getConnection(driver, url, name, pw)));
			database.setDatabaseChangeLogTableName("liquibasechangelog");
			database.setDatabaseChangeLogLockTableName("liquibasechangeloglock");

			ClassLoader cl = MavenUtils.getArtifactClassloader(project, true,
					true, getClass(), getLog(), true);
			ResourceAccessor mFO = new ClassLoaderResourceAccessor(cl);
			ResourceAccessor fsFO = new FileSystemResourceAccessor();
			Liquibase liquibase = new Liquibase(changeLogFile,
					new CompositeResourceAccessor(mFO, fsFO), database);
			liquibase.update((String) null);
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (database != null) {
					database.getConnection().close();
				}
			} catch (Exception e) { /* ignore */
			}
		}
	}
	
	private static Connection getConnection(String driver, String url, String name, String pw) throws Exception {
		Class.forName(driver);
		return DriverManager.getConnection(url, name, "");
	}

	private static String getPort(String url) {

		// in a string like this:
		// jdbc:mysql://localhost:3306/openmrs?autoReconnect=true
		// look for something like this :3306/
		String regex = ":[0-9]+/";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(url);

		matcher.find();
		String port = matcher.group();
		port = port.replace(":", "");
		port = port.replace("/", "");

		return port;
	}
}
