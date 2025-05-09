import net.fabricmc.loom.task.prod.ClientProductionRunTask

plugins {
	id("xaeroplus-all.conventions")
	id("xaeroplus-platform.conventions")
}

architectury {
	platformSetupLoomIde()
	fabric()
}

loom {
	accessWidenerPath = project(":common").loom.accessWidenerPath
	runs {
		getByName("client") {
			ideConfigGenerated(true)
			client()
		}
	}
}

val common: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating
configurations.compileClasspath.get().extendsFrom(common)
configurations.runtimeClasspath.get().extendsFrom(common)
configurations.getByName("developmentFabric").extendsFrom(common)

afterEvaluate {
	loom.runs.configureEach {
		// https://fabricmc.net/wiki/tutorial:mixin_hotswaps
		vmArg("-javaagent:${ configurations.compileClasspath.get().find { it.name.contains("sponge-mixin") } }")
//		vmArg("-Dmixin.debug.export=true")
	}
}

val worldmap_version_fabric: String by project.properties
val minimap_version_fabric: String by project.properties
val minecraft_version: String by project.properties
val destArchiveVersion = "${project.version}+${loom.platform.get().id()}-${minecraft_version}"
val destArchiveClassifier = "WM${worldmap_version_fabric}-MM${minimap_version_fabric}"

dependencies {
	modImplementation(libs.fabric.loader)
	modApi(libs.fabric.api)
	shadowCommon(libs.sqlite)
	implementation(libs.sqlite)
	modImplementation(libs.worldmap.fabric)
	modImplementation(libs.minimap.fabric)
    modCompileOnly(libs.baritone.fabric)
    modImplementation(libs.waystones.fabric)
	modImplementation(libs.balm.fabric)
	modCompileOnly(libs.fabric.waystones)
	modRuntimeOnly(libs.immediatelyfast)
	modImplementation(libs.modmenu)
    modImplementation(libs.sodium.fabric)
//	modRuntimeOnly(libs.fpsdisplay)
	modRuntimeOnly(libs.cloth.config.fabric) {
		exclude(group = "net.fabricmc.fabric-api")
	}
	implementation(include(libs.caffeine.get())!!)
	implementation(include(libs.lambdaEvents.get())!!)
	implementation(include(libs.oldbiomes.get())!!)
	productionRuntimeMods(libs.minimap.fabric)
	productionRuntimeMods(libs.worldmap.fabric)
	productionRuntimeMods(libs.fabric.api)

	common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionFabric")) { isTransitive = false }
}

tasks {
	processResources {
		filesMatching("fabric.mod.json") {
			expand(mapOf(
				"version" to project.version,
				"worldmap_version" to worldmap_version_fabric,
				"minimap_version" to minimap_version_fabric
			))
		}
	}

	shadowJar {
		configurations = listOf(shadowCommon)
		exclude("architectury.common.json")
	}

	remapJar {
		injectAccessWidener = true
		dependsOn(shadowJar)
		inputFile.set(shadowJar.get().archiveFile)
		archiveVersion = destArchiveVersion
		archiveClassifier = destArchiveClassifier
	}

	register<ClientProductionRunTask>("runProdTest") {
		this.jvmArgs.add("-DXP_CI_TEST")
	}

	register<ClientProductionRunTask>("runProd") {

	}
}

val javaComponent = components.findByName("java") as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(project.configurations.getByName("shadowRuntimeElements")) {
	skip()
}
