package telematics.teltonika.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.query.filter.ParentPredicate;
import org.openremote.model.query.filter.RealmPredicate;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.teltonika.TeltonikaConfigurationAsset;
import org.openremote.model.teltonika.TeltonikaModelConfigurationAsset;
import org.openremote.model.teltonika.TeltonikaParameter;
import telematics.teltonika.TeltonikaConfiguration;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static org.openremote.model.syslog.SyslogCategory.API;

public class TeltonikaConfigurationFactory {

	private static final Logger LOG = SyslogCategory.getLogger(API, TeltonikaConfigurationFactory.class);

	public static TeltonikaConfiguration createConfiguration(AssetStorageService assetStorageService, TimerService timerService, String fileLocation) {
		try{
			return getConfig(assetStorageService, timerService, fileLocation);
		} catch (Exception e) {
			if(e instanceof IndexOutOfBoundsException) {
				LOG.severe("More than 1 Master Teltonika configurations found! Shutting down.");
				throw e;
			} else if (e instanceof IllegalStateException) {
				LOG.severe("No Master Teltonika configuration found! Creating default configuration.");
				initializeConfigurationAssets(fileLocation, assetStorageService);
				return getConfig(assetStorageService, timerService, fileLocation);
			}
			throw e;
		}

	}

	private static TeltonikaConfiguration getConfig(AssetStorageService assetStorageService, TimerService timerService, String fileLocation) {
		List<TeltonikaConfigurationAsset> masterAssets = assetStorageService.findAll(
						new AssetQuery()
								.types(TeltonikaConfigurationAsset.class)
								.realm(new RealmPredicate("master"))

				)
				.stream()
				.map(asset -> (TeltonikaConfigurationAsset) asset)
				.toList();


		if (masterAssets.size() > 1) {
			throw new IndexOutOfBoundsException("More than 1 Master Teltonika configurations found! Shutting down.");
		}
		if (masterAssets.isEmpty()) {
			throw new IllegalStateException("No Master Teltonika configuration found! You need to create a new default configuration.");

		}

		List<TeltonikaModelConfigurationAsset> modelAssets = assetStorageService.findAll(
						new AssetQuery()
								.types(TeltonikaModelConfigurationAsset.class)
								.realm(new RealmPredicate("master"))
								.parents(new ParentPredicate(masterAssets.get(0).getId()))
				)
				.stream()
				.map(asset -> (TeltonikaModelConfigurationAsset) asset)
				.toList();

		return new TeltonikaConfiguration(masterAssets.get(0), modelAssets, new Date(timerService.getCurrentTimeMillis()));

	}

	private static void initializeConfigurationAssets(String fileLocation, AssetStorageService assetStorageService) {
		// Create initial configuration
		TeltonikaConfigurationAsset rootConfig = new TeltonikaConfigurationAsset("Teltonika Device Configuration");
		TeltonikaModelConfigurationAsset fmc003 = new TeltonikaModelConfigurationAsset("FMC003");

		rootConfig.setEnabled(true);
		rootConfig.setCheckForImei(false);
		rootConfig.setDefaultModelNumber("FMC003");
		rootConfig.setCommandTopic("sendToDevice");
		rootConfig.setResponseTopic("response");
		rootConfig.setStorePayloads(false);

		fmc003.setModelNumber("FMC003");
		ObjectMapper mapper = new ObjectMapper();
		try {
			TeltonikaParameter[] params = mapper.readValue(fileLocation, TeltonikaParameter[].class);
			fmc003.setParameterData(params);

		} catch (JsonProcessingException e) {
			throw new RuntimeException("Could not parse Teltonika Parameter JSON file");
		}

		rootConfig.setRealm("master");
		fmc003.setRealm("master");

		rootConfig = assetStorageService.merge(rootConfig);

		fmc003.setParent(rootConfig);

		assetStorageService.merge(fmc003);

	}

	public static void refreshInstance(AssetStorageService assetStorageService, TimerService timerService, String fileLocation) {
		createConfiguration(assetStorageService, timerService, fileLocation);
	}

}
