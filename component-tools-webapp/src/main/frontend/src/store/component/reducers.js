/**
 *  Copyright (C) 2006-2018 Talend Inc. - www.talend.com
 *
 *  Licensed under the Apache License, Version 2.0 (the 'License');
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an 'AS IS' BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import {
	GET_COMPONENT_LOADING,
	GET_COMPONENT_OK,
	BACK_TO_COMPONENT_EDIT,
	SUBMIT_COMPONENT,
} from '../constants';

export default (state = {}, action) => {
	switch (action.type) {
		case GET_COMPONENT_LOADING:
			return {
				...state,
				isLoading: true,
			};
		case GET_COMPONENT_OK:
			return {
				...state,
				uiSpec: action.uiSpec,
				isLoading: false,
				submitted: false,
			};
		case SUBMIT_COMPONENT:
			return {
				...state,
				uiSpec: {
					...state.uiSpec,
					properties: action.properties,
				},
				submitted: true,
			};
		case BACK_TO_COMPONENT_EDIT:
			return {
				...state,
				submitted: false,
			};
		default:
			return state;
	}
}
