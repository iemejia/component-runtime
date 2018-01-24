/**
 *  Copyright (C) 2006-2018 Talend Inc. - www.talend.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import { CircularProgress } from '@talend/react-components';
import { UIForm } from '@talend/react-forms/lib/UIForm';
import TalendComponentKitTrigger from 'component-kit.js';

function NoSelectedComponent() {
	return (
		<div>
			<h1>No component selected</h1>
			<p>Click on a component to see its form</p>
		</div>
	);
}

class Detail extends React.Component {
	constructor(props) {
		super(props);
		this.trigger = new TalendComponentKitTrigger({ url: 'api/v1/application/action' });
		this.onTrigger = this.onTrigger.bind(this);
		this.onSubmit = this.onSubmit.bind(this);
	}

	onTrigger(event, payload) {
		return this.trigger.onDefaultTrigger(event, payload);
	}

	onSubmit(event, payload) {
		this.props.onSubmit(event, payload);
	}

	render() {
		if (this.props.isLoading) {
			return (<CircularProgress />);
		} else if (!this.props.uiSpec) {
			return (<NoSelectedComponent/>);
		} else if (this.props.submitted) {
			return (
				<div>
					<pre>{JSON.stringify(this.props.uiSpec.properties, undefined, 2)}</pre>
					<button className="btn btn-success" onClick={this.props.backToComponentEdit}>Back to form</button>
				</div>
			);
		} else {
			return (
				<UIForm
					data={this.props.uiSpec}
					onTrigger={this.onTrigger}
					onSubmit={this.onSubmit}
				/>
			);
		}
	}
}

export default Detail;
