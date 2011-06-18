/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.illinois.bundleupdater;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.IProfileRegistry;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.operations.ProvisioningSession;
import org.eclipse.equinox.p2.operations.UpdateOperation;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class Updater implements IStartup {

	private static final String UPDATE_SITE_URI = "file:<path to where the git repository is cloned>/BundleUpdater/edu.illinois.bundleupdater.updatesite/";

	private URI getUpdateSiteURI() {
		try {
			return new URI(UPDATE_SITE_URI);
		} catch (URISyntaxException e) {
			Activator.getDefault().logErrorStatus("Invalid update site URI", e);
		}
		return null;
	}

	@Override
	public void earlyStartup() {
		BundleContext context = Activator.getContext();
		ServiceReference serviceReference = context
				.getServiceReference(IProvisioningAgentProvider.SERVICE_NAME);
		if (serviceReference == null)
			return;

		IProvisioningAgentProvider agentProvider = (IProvisioningAgentProvider) context
				.getService(serviceReference);
		try {
			final IProvisioningAgent agent = agentProvider.createAgent(null);

			IMetadataRepositoryManager metadataRepositoryManager = (IMetadataRepositoryManager) agent
					.getService(IMetadataRepositoryManager.SERVICE_NAME);
			IArtifactRepositoryManager artifactRepositoryManager = (IArtifactRepositoryManager) agent
					.getService(IArtifactRepositoryManager.SERVICE_NAME);

			metadataRepositoryManager.addRepository(getUpdateSiteURI());
			artifactRepositoryManager.addRepository(getUpdateSiteURI());

			IMetadataRepository metadataRepository = metadataRepositoryManager
					.loadRepository(getUpdateSiteURI(),
							new NullProgressMonitor());

			final IProfileRegistry registry = (IProfileRegistry) agent
					.getService(IProfileRegistry.SERVICE_NAME);

			System.err.println("profiles="
					+ Arrays.toString(registry.getProfiles()));

			final IProfile profile = registry.getProfile(IProfileRegistry.SELF);

			System.out.println("self profile=" + profile);

			Collection<IInstallableUnit> metadataIUs = metadataRepository
					.query(QueryUtil.createLatestIUQuery(),
							new NullProgressMonitor()).toUnmodifiableSet();

			System.err.println("metadataIUs=" + metadataIUs);

			IQuery<IInstallableUnit> query = QueryUtil
					.createIUQuery(Activator.PLUGIN_ID
							+ ".feature.feature.group");
			Collection<IInstallableUnit> iusToUpdate = profile.query(query,
					null).toUnmodifiableSet();

			System.err.println("iusToUpdate=" + iusToUpdate);

			ProvisioningSession provisioningSession = new ProvisioningSession(
					agent);

			final UpdateOperation updateOperation = new UpdateOperation(
					provisioningSession, iusToUpdate);

			IStatus modalResolution = updateOperation
					.resolveModal(new NullProgressMonitor());

			Activator.getDefault().log(modalResolution);
			System.err.println("modalResolution=" + modalResolution.toString());

			boolean profileHasScheduledOperations = provisioningSession
					.hasScheduledOperationsFor(profile.getProfileId());

			System.out.println("Profile "
					+ (profileHasScheduledOperations ? "has" : "does not have")
					+ " scheduled operation jobs.");
			if (modalResolution.isOK()) {
				Display.getDefault().syncExec(new Runnable() {

					@Override
					public void run() {
						runCommand("org.eclipse.equinox.p2.ui.sdk.update",
								"Failed to open the check for updates dialog",
								null);
					}
				});

			}
		} catch (ProvisionException e) {
			Activator.getDefault().logErrorStatus(
					"Provisioning exception while checking for updates", e);
		}
	}

	private static void runCommand(String commandId, String errorMessage,
			Event event) {
		ICommandService commandService = (ICommandService) PlatformUI
				.getWorkbench().getService(ICommandService.class);
		Command command = commandService.getCommand(commandId);
		if (!command.isDefined()) {
			return;
		}
		IHandlerService handlerService = (IHandlerService) PlatformUI
				.getWorkbench().getService(IHandlerService.class);
		try {
			handlerService.executeCommand(commandId, event);
		} catch (ExecutionException e) {
			Activator.getDefault().logErrorStatus(errorMessage, e);
		} catch (NotDefinedException e) {
			Activator.getDefault().logErrorStatus(errorMessage, e);
		} catch (NotEnabledException e) {
			Activator.getDefault().logErrorStatus(errorMessage, e);
		} catch (NotHandledException e) {
			Activator.getDefault().logErrorStatus(errorMessage, e);
		}
	}

}
