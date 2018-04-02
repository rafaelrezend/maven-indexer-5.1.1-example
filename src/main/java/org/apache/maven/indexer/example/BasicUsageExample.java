package org.apache.maven.indexer.example;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

//import org.apache.lucene.index.MultiFields;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Field;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.IteratorSearchRequest;
import org.apache.maven.index.IteratorSearchResponse;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
import org.apache.maven.index.updater.WagonHelper;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.version.InvalidVersionSpecificationException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class BasicUsageExample
{
    public static void main( String[] args )
        throws Exception
    {
        final BasicUsageExample basicUsageExample = new BasicUsageExample();
        basicUsageExample.perform();
    }
    
    // Server configuration
    private static final String REPOSITORY_URL = "http://localhost:9292/content/repositories/jenkins-releases/";
//    private static final String REPOSITORY_URL = "https://repo.jenkins-ci.org/releases/";
    
    // Search parameters
    private static final String GROUP_ID = "org.jenkins-ci.plugins";
    private static final String ARTIFACT_ID = "timestamper";
    private static final String MIN_VERSION = "1.8.2";
    private static final String PACKAGING = "hpi";
    
    // ==

    private final PlexusContainer plexusContainer;

    private final Indexer indexer;

    private final IndexUpdater indexUpdater;

    private final Wagon httpWagon;

    private IndexingContext centralContext;
    
    private RepositorySystem repositorySystem;
    private ArtifactResolver artifactResolver;

    public BasicUsageExample()
        throws PlexusContainerException, ComponentLookupException
    {
        // here we create Plexus container, the Maven default IoC container
        // Plexus falls outside of MI scope, just accept the fact that
        // MI is a Plexus component ;)
        // If needed more info, ask on Maven Users list or Plexus Users list
        // google is your friend!
        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
        config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
        this.plexusContainer = new DefaultPlexusContainer( config );

        // lookup the indexer components from plexus
        this.indexer = plexusContainer.lookup( Indexer.class );
        this.indexUpdater = plexusContainer.lookup( IndexUpdater.class );
        // lookup wagon used to remotely fetch index
        this.httpWagon = plexusContainer.lookup( Wagon.class, "http" );
        
        this.repositorySystem = plexusContainer.lookup(RepositorySystem.class);
        this.artifactResolver = plexusContainer.lookup(ArtifactResolver.class);
        
        if (artifactResolver == null) System.out.println("#+#+#+# artifactResolver is null");
        else System.out.println("#+#+#+# artifactResolver is NOT null");
        
        if (repositorySystem == null) System.out.println("#+#+#+# repositorySystem is null");
        else System.out.println("#+#+#+# repositorySystem is NOT null");
        

    }

    public void perform()
        throws IOException, ComponentLookupException, InvalidVersionSpecificationException
    {
        // Files where local cache is (if any) and Lucene Index should be located
        File centralLocalCache = new File( "target/central-cache" );
        File centralIndexDir = new File( "target/central-index" );

        // Creators we want to use (search for fields it defines)
        List<IndexCreator> indexers = new ArrayList<IndexCreator>();
        indexers.add( plexusContainer.lookup( IndexCreator.class, "min" ) );
        indexers.add( plexusContainer.lookup( IndexCreator.class, "jarContent" ) );
//        indexers.add( plexusContainer.lookup( IndexCreator.class, "maven-plugin" ) );

        // Create context for central repository index
        centralContext =
            indexer.createIndexingContext( "myrepo", "myrepo", centralLocalCache, centralIndexDir,
                                           REPOSITORY_URL, null, true, true, indexers );

        // Update the index (incremental update will happen if this is not 1st run and files are not deleted)
        // This whole block below should not be executed on every app start, but rather controlled by some configuration
        // since this block will always emit at least one HTTP GET. Central indexes are updated once a week, but
        // other index sources might have different index publishing frequency.
        // Preferred frequency is once a week.
        if ( true )
        {
            System.out.println( "Updating Index..." );
            System.out.println( "This might take a while on first run, so please be patient!" );
            
            // Create ResourceFetcher implementation to be used with IndexUpdateRequest
            // Here, we use Wagon based one as shorthand, but all we need is a ResourceFetcher implementation
            TransferListener listener = new AbstractTransferListener()
            {
                public void transferStarted( TransferEvent transferEvent ) {
                    System.out.print( "  Downloading " + transferEvent.getResource().getName());
                }

                public void transferProgress( TransferEvent transferEvent, byte[] buffer, int length ) {
                }

                public void transferCompleted( TransferEvent transferEvent ) {
                    System.out.println( " - Done" );
                }
            };
            ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher( httpWagon, listener, null, null );
            
            IndexUpdateRequest updateRequest = new IndexUpdateRequest( centralContext, resourceFetcher );
            IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex( updateRequest );
            
            if ( updateResult.isFullUpdate() )
                System.out.println( "Full update happened!" );
            else
				System.out.println("Local index found! Partial or no update happened!" );
            
            System.out.println();
        }

        System.out.println();
        System.out.println( "Using index" );
        System.out.println( "===========" );
        System.out.println();

        // ====
        // Case:
        // Search for all GAVs with known G and A and having version greater than V

        final BooleanQuery query = new BooleanQuery();
        // query for specific packaging
        System.out.println("Query : Packaging must be " + PACKAGING);
        query.add( indexer.constructQuery( MAVEN.PACKAGING, new SourcedSearchExpression( PACKAGING ) ), Occur.MUST );
        
        // query for artifacts only (no classifier)
        System.out.println("Query : Artifacts only (no classifier)");
        query.add( indexer.constructQuery( MAVEN.CLASSIFIER, new SourcedSearchExpression( Field.NOT_PRESENT ) ), Occur.MUST_NOT );
        
		System.out.println("Searching with Query...");
        final IteratorSearchRequest request =
            new IteratorSearchRequest( query, Collections.singletonList( centralContext ) );
        final IteratorSearchResponse response = indexer.searchIterator( request );
        
        System.out.println("\nResults:");
        int pluginAmount = 0;
        for ( ArtifactInfo ai : response )
        {
            System.out.println( ai.toString() );
            pluginAmount++;
            
            Iterator<Entry<String, String>> attrs = ai.getAttributes().entrySet().iterator();
            while (attrs.hasNext()) {
				Entry<String, String> object = attrs.next();
				System.out.println("##### : " + object.getKey() + " : " + object.getValue());
			}
            
            Collection<Field> attrscoll = ai.getFields();
            for (Field field : attrscoll) {
				System.out.println("+++++ : " + field.toString());
			}
            
            Artifact artifact = this.repositorySystem.createArtifactWithClassifier(ai.groupId, ai.artifactId, ai.version, "pom", null);
            
            if (artifact == null) System.out.println("#+#+#+# artifact is null");
            else System.out.println("#+#+#+# artifact is NOT null");
            
            ArtifactResolutionRequest artifactRequest = new ArtifactResolutionRequest().setArtifact(artifact);
            
            if (artifactRequest == null) System.out.println("#+#+#+# artifactRequest is null");
            else System.out.println("#+#+#+# artifactRequest is NOT null");
            
            ArtifactResolutionResult artifactResult = this.artifactResolver.resolve(artifactRequest).setRepositories(repositories);
            
            if (artifactResult == null) System.out.println("#+#+#+# artifactResult is null");
            else System.out.println("#+#+#+# artifactResult is NOT null");
            
            Set<Artifact> artifactsss = artifactResult.getArtifacts();
            for (Artifact artifact2 : artifactsss) {
				System.out.println("++ : " + artifact2.getDownloadUrl());
			}
            
//            ArtifactResolver res = new ...
//            		how is backend-update-center2 (original and my fork) actually resolving it?
//            		do we need to download the file? :-(
            
        }
        
        System.out.println();
        System.out.println("Plugins found: " + pluginAmount);
    }
}
